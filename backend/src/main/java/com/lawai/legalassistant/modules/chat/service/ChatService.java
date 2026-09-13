package com.lawai.legalassistant.modules.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.ai.prompt.PromptTemplates;
import com.lawai.legalassistant.common.compliance.SensitiveWordFilter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;
import com.lawai.legalassistant.modules.chat.dto.SessionVO;
import com.lawai.legalassistant.modules.chat.entity.ChatMessage;
import com.lawai.legalassistant.modules.chat.entity.ChatSession;
import com.lawai.legalassistant.modules.chat.mapper.ChatMessageMapper;
import com.lawai.legalassistant.modules.chat.mapper.ChatSessionMapper;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.service.RagService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    /** 历史上下文最大字符数（v1.9.0 新增，防止长对话 token 超限） */
    private static final int MAX_HISTORY_CHARS = 4000;
    /**
     * 异步问答外层总超时（秒）。
     * v1.16.1：此前 90s 小于降级链聚合上限（B.AI 同步可达 25s×重试2≈50s + GLM 60s≈110s），
     * 在 B.AI/GLM 均慢或不可用时外层先超时，Agnes 兜底永远来不及生效，用户得到"AI 回复超时"而非答案。
     * 提高至可覆盖整条链并给 Agnes 兜底留余量；常见路径(主模型快速应答)不受影响。
     */
    private static final int ASYNC_AI_TIMEOUT_SECONDS = 150;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final RagService ragService;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                       RagService ragService, AiRouter aiRouter, ObjectMapper objectMapper,
                       SensitiveWordFilter sensitiveWordFilter) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.ragService = ragService;
        this.aiRouter = aiRouter;
        this.objectMapper = objectMapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    /**
     * AI 异步调用专用线程池，避免长阻塞 I/O 耗尽 ForkJoinPool.commonPool()。
     * <p>
     * v1.9.2 安全修复：使用有界队列（容量 100）+ CallerRunsPolicy 拒绝策略，
     * 防止高并发下任务堆积导致 OOM（Fly.io vm 仅 512MB 内存）。
     * 队列满时由调用线程执行，形成背压。
     */
    private final ExecutorService aiExecutor = new ThreadPoolExecutor(
            8, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "ai-async-executor");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * SSE 心跳调度器（v1.18，审查 P2#10）：流式期间每 15s 发送注释帧保活，
     * 覆盖 RAG 检索 + 模型首包的空闲期。单线程守护池，随应用关闭。
     */
    private final java.util.concurrent.ScheduledExecutorService heartbeatScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

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
     * <p>
     * v1.7.0 优化：将 N+1 查询改为批量查询，1+N 次降为 1+1 次。
     */
    public List<SessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt));
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询所有会话的最后一条消息（1 次查询替代 N 次）
        List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        List<ChatMessage> lastMessages = messageMapper.selectLastMessagesBySessionIds(sessionIds);
        Map<Long, String> lastMessageMap = new LinkedHashMap<>(lastMessages.size());
        for (ChatMessage m : lastMessages) {
            lastMessageMap.put(m.getSessionId(), m.getContent());
        }

        List<SessionVO> result = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            SessionVO vo = toSessionVO(s);
            String lastContent = lastMessageMap.get(s.getId());
            if (lastContent != null) {
                vo.setLastMessage(lastContent);
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
        // v1.11.0 修复：userId 为 null 时主动抛 BusinessException，避免下游 NPE
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
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
     * 批量删除会话
     * <p>
     * v1.11.0 修复 H-8：将 N+1 查询改为一次 selectList 校验归属 + deleteBatchIds 批量删除，
     * 并添加 @Transactional 保证部分失败时整体回滚，避免留下不一致状态。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteSessions(Long userId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        // 一次性查询所有归属当前用户的会话，过滤掉不属于该用户的 id
        List<ChatSession> owned = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .select(ChatSession::getId)
                        .eq(ChatSession::getUserId, userId)
                        .in(ChatSession::getId, sessionIds));
        if (owned.isEmpty()) {
            log.warn("批量删除会话跳过: userId={}, 无归属会话 ids={}", userId, sessionIds);
            return;
        }
        List<Long> ownedIds = owned.stream().map(ChatSession::getId).toList();
        // 级联删除依赖 ON DELETE CASCADE（chat_message 表已配 FK CASCADE）
        sessionMapper.deleteBatchIds(ownedIds);
        log.info("批量删除会话: userId={}, count={}", userId, ownedIds.size());
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
     * 发送消息（异步模式）
     * <p>
     * 同步保存用户消息后立即返回用户消息 ID，AI 调用在后台异步执行。
     * 前端通过轮询 GET /sessions/{id}/messages 获取 AI 回复。
     * 避免同步调用 AI 超过 Fly.io proxy 60 秒超时。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @return 用户消息 ID
     */
    public Long sendMessageAsync(Long userId, Long sessionId, String content) {
        // 校验登录
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        // 校验内容
        if (content == null || content.isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }
        // 敏感词过滤：命中则拒绝发送
        if (sensitiveWordFilter.contains(content)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "消息包含敏感内容，请修改后重试");
        }
        // 过滤处理（即使未命中也执行，确保内容安全）
        final String filteredContent = sensitiveWordFilter.filter(content);
        // 校验会话
        ChatSession session = getSession(userId, sessionId);

        // 1. 加载近 6 轮历史（在保存当前用户消息前加载，避免包含当前问题）
        List<ChatMessage> recent = messageMapper.selectRecent(sessionId, HISTORY_LIMIT);
        Collections.reverse(recent);
        String history = buildHistory(recent);

        // 2. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(filteredContent);
        messageMapper.insertMessage(userMsg);
        touchSession(sessionId);

        // 3. 异步执行 RAG 检索 + AI 调用 + 保存 AI 消息
        // v1.11.0 修复 C-1：使用 AtomicBoolean 保证超时与正常完成只保存一次，避免重复消息
        java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);
        CompletableFuture.runAsync(() -> {
            try {
                // RAG 检索（embedding 模型不可用时降级为无上下文）
                List<RetrievedChunk> chunks = Collections.emptyList();
                String citationsJson = null;
                try {
                    chunks = ragService.retrieveDeep(filteredContent, RAG_TOP_K, userId);
                    citationsJson = buildCitationsJson(chunks);
                } catch (Exception e) {
                    log.warn("RAG 检索失败，降级为无上下文对话: {}", e.getMessage());
                }
                String context = buildContext(chunks);
                String userPrompt = PromptTemplates.render(PromptTemplates.LEGAL_QA_USER_TEMPLATE,
                        Map.of("context", context, "history", history, "question", filteredContent));
                String aiContent = aiRouter.chat(PromptTemplates.LEGAL_QA_SYSTEM, userPrompt);
                if (saved.compareAndSet(false, true)) {
                    saveAssistantMessage(sessionId, aiContent, citationsJson);
                    log.info("异步 AI 回复完成: sessionId={}", sessionId);
                    // 如果会话标题是默认的"新对话"，自动生成标题
                    if ("新对话".equals(session.getTitle())) {
                        autoRenameSession(sessionId, filteredContent);
                    }
                }
            } catch (Exception e) {
                log.error("异步 AI 调用失败: sessionId={}", sessionId, e);
                if (saved.compareAndSet(false, true)) {
                    // 保存错误提示消息
                    saveAssistantMessage(sessionId, "AI 服务暂时不可用，请稍后重试。", null);
                }
            }
        }, aiExecutor).orTimeout(ASYNC_AI_TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> {
            log.error("异步 AI 调用超时: sessionId={}", sessionId, ex);
            if (saved.compareAndSet(false, true)) {
                saveAssistantMessage(sessionId, "AI 回复超时，请稍后重试", null);
            }
            return null;
        });

        return userMsg.getId();
    }

    /**
     * 发送图片消息（异步模式）
     * <p>
     * 先调用 AI 识别图片中的法律问题，保存识别出的问题为用户消息，
     * 再调用 AI 生成法律回答，保存为助手消息。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param imageBytes 图片字节数组
     * @param mimeType  图片 MIME 类型
     * @return 用户消息 ID
     */
    public Long sendMessageWithImage(Long userId, Long sessionId, byte[] imageBytes, String mimeType) {
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        getSession(userId, sessionId);

        List<ChatMessage> recent = messageMapper.selectRecent(sessionId, HISTORY_LIMIT);
        Collections.reverse(recent);
        String history = buildHistory(recent);

        // 先保存一条占位用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent("[图片消息] 正在识别图片内容...");
        messageMapper.insertMessage(userMsg);
        touchSession(sessionId);

        // v1.11.0 修复 C-1：使用 AtomicBoolean 保证超时、异常、正常完成只保存一次助手消息，避免重复消息
        java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);
        CompletableFuture.runAsync(() -> {
            try {
                // 第一步：识别图片中的法律问题
                String extractPrompt = "请识别图片中的法律问题或法律相关内容，提取并整理为一个清晰的法律问题。只输出问题本身，不要附加解释。如果图片中没有法律相关问题，请回复：未识别到法律相关问题。";
                String recognizedQuestion = aiRouter.chatWithImage(
                        "你是一个法律问题识别助手，请从图片中提取法律问题。",
                        extractPrompt, imageBytes, mimeType);

                // 更新用户消息为识别出的问题
                ChatMessage updateUserMsg = new ChatMessage();
                updateUserMsg.setId(userMsg.getId());
                // 如果未识别到法律问题，保存提示消息
                if (recognizedQuestion.contains("未识别到法律相关问题")) {
                    if (saved.compareAndSet(false, true)) {
                        saveAssistantMessage(sessionId,
                                "抱歉，未能从图片中识别到法律相关问题。请上传包含法律内容的图片，或直接输入您的问题。",
                                null);
                    }
                    return;
                }

                // v1.18（审查 P2#7）：识别结果与文本消息同标准过敏感词——此前图片路径完全绕过过滤，
                // 识别出的内容可绕过校验直接落库并参与 RAG/AI
                if (sensitiveWordFilter.contains(recognizedQuestion)) {
                    ChatMessage blockMsg = new ChatMessage();
                    blockMsg.setId(userMsg.getId());
                    blockMsg.setContent("[图片消息]（识别内容未通过安全校验）");
                    messageMapper.updateById(blockMsg);
                    if (saved.compareAndSet(false, true)) {
                        saveAssistantMessage(sessionId, "图片中的内容未通过安全校验，无法处理。请更换图片或直接输入您的问题。", null);
                    }
                    return;
                }
                final String filteredRecognized = sensitiveWordFilter.filter(recognizedQuestion);
                updateUserMsg.setContent(filteredRecognized);
                messageMapper.updateById(updateUserMsg);

                // 第二步：RAG 检索 + AI 回答
                List<RetrievedChunk> chunks = Collections.emptyList();
                String citationsJson = null;
                try {
                    chunks = ragService.retrieveDeep(filteredRecognized, RAG_TOP_K, userId);
                    citationsJson = buildCitationsJson(chunks);
                } catch (Exception e) {
                    log.warn("RAG 检索失败，降级为无上下文对话: {}", e.getMessage());
                }
                String context = buildContext(chunks);
                String userPrompt = PromptTemplates.render(PromptTemplates.LEGAL_QA_USER_TEMPLATE,
                        Map.of("context", context, "history", history, "question", filteredRecognized));
                String aiContent = aiRouter.chat(PromptTemplates.LEGAL_QA_SYSTEM, userPrompt);
                if (saved.compareAndSet(false, true)) {
                    saveAssistantMessage(sessionId, aiContent, citationsJson);
                }

                // 第三步：自动命名
                ChatSession session = sessionMapper.selectById(sessionId);
                if (session != null && "新对话".equals(session.getTitle())) {
                    autoRenameSession(sessionId, filteredRecognized);
                }

                log.info("图片消息处理完成: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("图片消息处理失败: sessionId={}", sessionId, e);
                if (saved.compareAndSet(false, true)) {
                    saveAssistantMessage(sessionId, "图片识别失败，请稍后重试或直接输入您的问题。", null);
                }
            }
        }, aiExecutor).orTimeout(ASYNC_AI_TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> {
            log.error("图片消息处理超时: sessionId={}", sessionId, ex);
            if (saved.compareAndSet(false, true)) {
                saveAssistantMessage(sessionId, "AI 回复超时，请稍后重试", null);
            }
            return null;
        });

        return userMsg.getId();
    }

    /**
     * SSE 流式问答
     * <p>
     * 同步保存用户消息，通过 SseEmitter 流式返回 AI 回复。
     * RAG 检索在流式调用前同步执行，检索结果随首批数据返回。
     * 流结束后异步保存完整 AI 消息。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @param emitter   SSE 发射器
     */
    public void streamChat(Long userId, Long sessionId, String content, SseEmitter emitter) {
        // v1.18（审查 V1-04）：流路径的最终超时裁决收敛到 emitter onTimeout 回调——
        // 此前外层 runAsync(...).orTimeout(150s) 体内 .subscribe() 非阻塞即返回，future 秒级正常完成，
        // orTimeout/exceptionally 永不触发，流式阶段实际只剩 emitter 155s 兜底且不发 error 事件。
        // 心跳/裁决回调在此处同步注册（早于任何异步动作），保证任意时刻超时都有裁决者。
        StringBuilder fullContent = new StringBuilder();
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<reactor.core.Disposable> subRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        // v1.18（审查 P2#10）：15s 注释帧保活，防止 RAG 检索+模型首包的空闲期被中间代理/CDN 掐断
        java.util.concurrent.ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (finished.get()) {
                return;
            }
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().comment("ping"));
                }
            } catch (Exception ignored) {
                // 客户端已断开时的发送异常交由 onError/onTimeout 回调收尾
            }
        }, 15, 15, TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            heartbeat.cancel(false);
            reactor.core.Disposable d = subRef.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        };
        emitter.onTimeout(() -> {
            if (finished.compareAndSet(false, true)) {
                log.warn("SSE 流式超时(emitter 155s)，保存半截内容并通知前端: sessionId={}", sessionId);
                savePartialAssistantMessage(sessionId, fullContent, null);
                trySendEvent(emitter, "error", "AI 回复超时，请稍后重试");
            }
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(t -> {
            finished.set(true);
            cleanup.run();
        });
        emitter.onCompletion(cleanup);

        // v1.18（审查 P2#6）：校验失败先发 error 事件再 complete——此前 completeWithError 后前端
        // 只见 200+空流，统一显示"AI 回复为空"，无法区分 未登录/敏感词/参数错误
        if (userId == null) {
            sendErrorAndComplete(emitter, "请先登录");
            return;
        }
        if (content == null || content.isBlank()) {
            sendErrorAndComplete(emitter, "消息内容不能为空");
            return;
        }
        // 敏感词过滤
        if (sensitiveWordFilter.contains(content)) {
            sendErrorAndComplete(emitter, "消息包含敏感内容，请修改后重试");
            return;
        }
        final String filteredContent = sensitiveWordFilter.filter(content);

        ChatSession session;
        try {
            session = getSession(userId, sessionId);
        } catch (Exception e) {
            log.warn("SSE 会话校验失败: sessionId={}, {}", sessionId, e.getMessage());
            sendErrorAndComplete(emitter, "会话不存在或无权限");
            return;
        }

        // 加载历史
        List<ChatMessage> recent = messageMapper.selectRecent(sessionId, HISTORY_LIMIT);
        Collections.reverse(recent);
        String history = buildHistory(recent);

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(filteredContent);
        messageMapper.insertMessage(userMsg);
        touchSession(sessionId);

        // 异步执行流式 AI 调用
        CompletableFuture.runAsync(() -> {
            try {
                // RAG 检索
                List<RetrievedChunk> chunks = Collections.emptyList();
                final String[] citationsHolder = {null};
                try {
                    chunks = ragService.retrieveDeep(filteredContent, RAG_TOP_K, userId);
                    citationsHolder[0] = buildCitationsJson(chunks);
                } catch (Exception e) {
                    log.warn("SSE RAG 检索失败，降级为无上下文: {}", e.getMessage());
                }
                final String citationsJson = citationsHolder[0];
                String context = buildContext(chunks);
                String userPrompt = PromptTemplates.render(PromptTemplates.LEGAL_QA_USER_TEMPLATE,
                        Map.of("context", context, "history", history, "question", filteredContent));

                // 发送引用来源（如果有）
                if (citationsJson != null) {
                    trySendEvent(emitter, "citations", citationsJson);
                }

                // 流式调用 AI。v1.11.0 H-12：subRef 保存 Disposable，emitter 回调 dispose，
                // 防止 emitter 超时/错误后上游 Flux 仍持续推送浪费 AI 配额与连接
                // （v1.18：回调已统一在方法开头注册，此处仅填充引用）
                subRef.set(aiRouter.streamChat(PromptTemplates.LEGAL_QA_SYSTEM, userPrompt)
                        .doOnNext(chunk -> {
                            fullContent.append(chunk);
                            trySendEvent(emitter, "chunk", chunk);
                        })
                        .doOnComplete(() -> {
                            if (finished.compareAndSet(false, true)) {
                                heartbeat.cancel(false);
                                // v1.18（审查 P2#2）：空回复不落库，与同步链 blank 守卫语义对齐
                                if (fullContent.isEmpty() || fullContent.toString().isBlank()) {
                                    log.warn("SSE 流式回复为空: sessionId={}", sessionId);
                                    trySendEvent(emitter, "error", "AI 回复为空，请稍后重试");
                                } else {
                                    saveAssistantMessage(sessionId, fullContent.toString(), citationsJson);
                                    trySendEvent(emitter, "done", "[DONE]");
                                    log.info("SSE 流式回复完成: sessionId={}", sessionId);
                                }
                            }
                            emitter.complete();
                            // v1.18（审查 P2#5）：自动命名是一次完整同步 AI 调用（最坏 ~130s），
                            // 移出 reactor 线程执行，避免并发多流时占满 event loop 拖慢所有进行中的流
                            if ("新对话".equals(session.getTitle())) {
                                CompletableFuture.runAsync(() -> autoRenameSession(sessionId, filteredContent), aiExecutor);
                            }
                        })
                        .doOnError(e -> {
                            if (finished.compareAndSet(false, true)) {
                                heartbeat.cancel(false);
                                log.error("SSE 流式调用失败: sessionId={}", sessionId, e);
                                // v1.18（审查 P2#4）：半截内容落库，避免前端已渲染内容刷新后消失
                                savePartialAssistantMessage(sessionId, fullContent, null);
                                trySendEvent(emitter, "error", "AI 服务暂时不可用");
                            }
                            emitter.complete();
                        })
                        .subscribe());
            } catch (Exception e) {
                log.error("SSE 流式问答异常: sessionId={}", sessionId, e);
                if (finished.compareAndSet(false, true)) {
                    heartbeat.cancel(false);
                }
                sendErrorAndComplete(emitter, "AI 服务暂时不可用");
            }
        }, aiExecutor);
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
     * v1.18（审查 P2#3）：SSE 发送统一助手。
     * synchronized 序列化 chunk/事件/心跳的并发发送（SseEmitter 非线程安全，
     * reactor 线程与容器回调线程可能同时 send/complete）；完成态后的发送异常属正常收尾竞态，静默降级。
     */
    private void trySendEvent(SseEmitter emitter, String event, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data));
            }
        } catch (IOException e) {
            log.warn("SSE 发送 {} 失败: {}", event, e.getMessage());
        } catch (Exception e) {
            log.debug("SSE 发送 {} 被拒绝(emitter 已完成): {}", event, e.getMessage());
        }
    }

    /**
     * v1.18（审查 P2#6）：SSE 错误统一出口——先发 error 事件（前端可感知具体原因），再 complete 收尾。
     */
    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        trySendEvent(emitter, "error", message);
        emitter.complete();
    }

    /**
     * v1.18（审查 P2#4）：半截内容落库——超时/中断时保存已生成部分，
     * 避免前端已渲染的内容在刷新后消失（用户消息已存、助手消息缺失的不一致状态）。
     */
    private void savePartialAssistantMessage(Long sessionId, StringBuilder partial, String citationsJson) {
        String content = partial == null ? "" : partial.toString();
        if (content.isBlank()) {
            return;
        }
        saveAssistantMessage(sessionId, content + "\n\n*（回复因超时中断，以上为已生成部分）*", citationsJson);
    }

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
     * 异步自动命名会话（当标题为默认"新对话"时）
     * 调用 AI 根据用户首条消息生成 4-8 字摘要
     */
    private void autoRenameSession(Long sessionId, String userMessage) {
        try {
            String titlePrompt = "请将以下用户问题概括为4-8个字的标题，直接输出标题文字，不要加引号或标点：\n" + userMessage;
            String title = aiRouter.chat("你是一个标题生成助手，只输出简短标题。", titlePrompt);
            // 清理标题：去除引号、换行、首尾空格，截断到8字
            title = title.replaceAll("[\"'\\u201c\\u201d\\u2018\\u2019\\u300c\\u300d\\u3010\\u3011]", "").replaceAll("[\\r\\n]", "").trim();
            if (title.length() > 8) {
                title = title.substring(0, 8);
            }
            if (!title.isBlank()) {
                ChatSession update = new ChatSession();
                update.setId(sessionId);
                update.setTitle(title);
                sessionMapper.updateById(update);
                log.info("会话自动命名: sessionId={}, title={}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("会话自动命名失败: sessionId={}, {}", sessionId, e.getMessage());
        }
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
     * 构造历史上下文（v1.9.0 优化：滑动窗口 + 截断，防止 token 超限）
     * <p>
     * 策略：从最近消息开始向前累加，当累计字符数超过 MAX_HISTORY_CHARS 时，
     * 截断更早的消息并标注"[更早的对话已省略]"，保证最近上下文完整。
     *
     * @param messages 历史消息列表（按时间正序，最旧在前）
     * @return 拼接后的历史上下文字符串
     */
    private String buildHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        // 从最近（列表末尾）向前累加，保留最近上下文
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        boolean truncated = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            String role = "user".equals(m.getRole()) ? "用户" : "助手";
            String line = role + "：" + m.getContent() + "\n";
            // 超出阈值且已有内容时，截断更早的消息
            if (totalChars + line.length() > MAX_HISTORY_CHARS && sb.length() > 0) {
                truncated = true;
                break;
            }
            sb.insert(0, line);
            totalChars += line.length();
        }
        if (truncated) {
            sb.insert(0, "[更早的对话已省略]\n");
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

    /**
     * 优雅关闭 AI 异步线程池
     */
    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
        aiExecutor.shutdown();
        try {
            if (!aiExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                aiExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
