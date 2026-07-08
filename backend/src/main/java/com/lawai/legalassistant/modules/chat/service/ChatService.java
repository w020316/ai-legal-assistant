package com.lawai.legalassistant.modules.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AgnesClient;
import com.lawai.legalassistant.ai.prompt.PromptTemplates;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.sse.SseHelper;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;
import com.lawai.legalassistant.modules.chat.dto.SessionVO;
import com.lawai.legalassistant.modules.chat.entity.ChatMessage;
import com.lawai.legalassistant.modules.chat.entity.ChatSession;
import com.lawai.legalassistant.modules.chat.mapper.ChatMessageMapper;
import com.lawai.legalassistant.modules.chat.mapper.ChatSessionMapper;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务
 * <p>
 * 提供会话 CRUD、消息历史查询、流式问答（RAG + SSE）与导出能力。
 * 对应设计方案 3.4 节核心问答数据流。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 历史轮数（每轮=user+assistant，故取 12 条） */
    private static final int HISTORY_LIMIT = 12;
    /** RAG 检索 Top-K */
    private static final int RAG_TOP_K = 5;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final RagService ragService;
    private final AgnesClient agnesClient;
    private final ObjectMapper objectMapper;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                       RagService ragService, AgnesClient agnesClient, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.ragService = ragService;
        this.agnesClient = agnesClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 新建会话
     */
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle((title == null || title.isBlank()) ? "新对话" : title);
        session.setStarred(false);
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 会话列表（按更新时间倒序），附带最后一条消息预览
     */
    public List<SessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt));
        List<SessionVO> result = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            SessionVO vo = toSessionVO(s);
            List<ChatMessage> recent = messageMapper.selectRecent(s.getId(), 1);
            if (!recent.isEmpty()) {
                vo.setLastMessage(recent.get(0).getContent());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 获取会话（校验归属）
     *
     * @throws BusinessException 会话不存在或不属于该用户
     */
    public ChatSession getSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "会话不存在或无权限");
        }
        return session;
    }

    /**
     * 更新会话（重命名/收藏），仅更新传入字段
     */
    public void updateSession(Long userId, Long sessionId, String title, Boolean starred) {
        getSession(userId, sessionId);
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        if (title != null) {
            update.setTitle(title);
        }
        if (starred != null) {
            update.setStarred(starred);
        }
        update.setUpdatedAt(Instant.now());
        sessionMapper.updateById(update);
    }

    /**
     * 删除会话（消息级联删除）
     */
    public void deleteSession(Long userId, Long sessionId) {
        getSession(userId, sessionId);
        sessionMapper.deleteById(sessionId);
    }

    /**
     * 消息历史（按时间正序）
     */
    public List<MessageVO> listMessages(Long userId, Long sessionId) {
        getSession(userId, sessionId);
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt));
        List<MessageVO> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            result.add(toMessageVO(m));
        }
        return result;
    }

    /**
     * 发送消息（核心方法，流式输出）
     * <p>
     * 流程：保存用户消息 → 加载历史 → RAG 检索 → 构造 Prompt → 流式输出 → 保存 AI 消息。
     * 所有错误通过 SSE error 事件返回，避免内容协商问题。
     *
     * @param userId    用户 ID（可能为 null，未登录）
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @return SseEmitter
     */
    public SseEmitter sendMessage(Long userId, Long sessionId, String content) {
        SseEmitter emitter = SseHelper.create();

        // 校验登录
        if (userId == null) {
            SseHelper.send(emitter, SseHelper.EVENT_ERROR,
                    Map.of("code", ResultCode.UNAUTHORIZED, "message", "请先登录"));
            SseHelper.complete(emitter);
            return emitter;
        }
        // 校验内容
        if (content == null || content.isBlank()) {
            SseHelper.send(emitter, SseHelper.EVENT_ERROR,
                    Map.of("code", ResultCode.PARAM_ERROR, "message", "消息内容不能为空"));
            SseHelper.complete(emitter);
            return emitter;
        }
        // 校验会话
        try {
            getSession(userId, sessionId);
        } catch (BusinessException e) {
            SseHelper.send(emitter, SseHelper.EVENT_ERROR,
                    Map.of("code", e.getCode(), "message", e.getMessage()));
            SseHelper.complete(emitter);
            return emitter;
        }

        try {
            // 1. 加载近 6 轮历史（在保存当前用户消息前加载，避免包含当前问题）
            List<ChatMessage> recent = messageMapper.selectRecent(sessionId, HISTORY_LIMIT);
            Collections.reverse(recent);
            String history = buildHistory(recent);

            // 2. 保存用户消息
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(content);
            messageMapper.insertMessage(userMsg);
            touchSession(sessionId);

            // 3. RAG 检索 Top5
            List<RetrievedChunk> chunks = ragService.retrieve(content, RAG_TOP_K);
            String context = buildContext(chunks);
            String citationsJson = buildCitationsJson(chunks);

            // 4. 构造 Prompt
            String userPrompt = PromptTemplates.render(PromptTemplates.LEGAL_QA_USER_TEMPLATE,
                    Map.of("context", context, "history", history, "question", content));

            // 5. 先推送引用来源
            SseHelper.send(emitter, SseHelper.EVENT_CITATIONS,
                    Map.of("citations", chunks));

            // 6. 流式输出
            StringBuilder fullContent = new StringBuilder();
            agnesClient.streamChat(PromptTemplates.LEGAL_QA_SYSTEM, userPrompt)
                    .subscribe(
                            token -> {
                                fullContent.append(token);
                                SseHelper.send(emitter, SseHelper.EVENT_TOKEN,
                                        Map.of("content", token));
                            },
                            error -> {
                                log.error("AI 流式输出失败", error);
                                SseHelper.send(emitter, SseHelper.EVENT_ERROR,
                                        Map.of("code", ResultCode.AI_SERVICE_ERROR, "message", "AI 服务暂时不可用"));
                                // 保存已生成的部分内容
                                if (fullContent.length() > 0) {
                                    saveAssistantMessage(sessionId, fullContent.toString(), citationsJson);
                                }
                                SseHelper.complete(emitter);
                            },
                            () -> {
                                // 7. 流结束，保存 AI 消息
                                Long msgId = saveAssistantMessage(sessionId, fullContent.toString(), citationsJson);
                                SseHelper.send(emitter, SseHelper.EVENT_DONE,
                                        Map.of("messageId", msgId != null ? msgId : 0,
                                                "tokens", fullContent.length()));
                                SseHelper.complete(emitter);
                            }
                    );
        } catch (Exception e) {
            log.error("发送消息处理失败", e);
            SseHelper.send(emitter, SseHelper.EVENT_ERROR,
                    Map.of("code", ResultCode.UNKNOWN, "message", "处理失败，请重试"));
            SseHelper.complete(emitter);
        }
        return emitter;
    }

    /**
     * 导出会话为 Markdown
     */
    public String exportSession(Long userId, Long sessionId) {
        ChatSession session = getSession(userId, sessionId);
        List<MessageVO> messages = listMessages(userId, sessionId);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(session.getTitle()).append("\n\n");
        for (MessageVO m : messages) {
            md.append("## ").append("user".equals(m.getRole()) ? "用户" : "AI 助手").append("\n\n");
            md.append(m.getContent()).append("\n\n");
        }
        return md.toString();
    }

    // ==================== 内部方法 ====================

    /**
     * 保存 AI 助手消息
     *
     * @return 消息 ID
     */
    private Long saveAssistantMessage(Long sessionId, String content, String citationsJson) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setCitations(citationsJson);
        msg.setTokens(content.length());
        messageMapper.insertMessage(msg);
        touchSession(sessionId);
        return msg.getId();
    }

    /**
     * 更新会话更新时间
     */
    private void touchSession(Long sessionId) {
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        update.setUpdatedAt(Instant.now());
        sessionMapper.updateById(update);
    }

    /**
     * 构造历史上下文文本
     */
    private String buildHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            String role = "user".equals(m.getRole()) ? "用户" : "助手";
            sb.append(role).append("：").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构造检索上下文文本
     */
    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "无相关检索资料";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (c.getTitle() != null) {
                sb.append("《").append(c.getTitle()).append("》");
            }
            if (c.getSource() != null) {
                sb.append(" 来源：").append(c.getSource());
            }
            sb.append("\n").append(c.getSnippet()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构造引用来源 JSON 字符串
     */
    private String buildCitationsJson(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> list = new ArrayList<>(chunks.size());
            for (RetrievedChunk c : chunks) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("docId", c.getDocId());
                item.put("title", c.getTitle());
                item.put("source", c.getSource());
                item.put("snippet", c.getSnippet());
                item.put("score", c.getScore());
                list.add(item);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("构造引用 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private SessionVO toSessionVO(ChatSession s) {
        SessionVO vo = new SessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setStarred(s.getStarred());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }

    private MessageVO toMessageVO(ChatMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setCitations(m.getCitations());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
