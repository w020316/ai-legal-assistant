package com.lawai.legalassistant.modules.chat.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.chat.dto.BatchDeleteRequest;
import com.lawai.legalassistant.modules.chat.dto.CreateSessionRequest;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;
import com.lawai.legalassistant.modules.chat.dto.SendMessageRequest;
import com.lawai.legalassistant.modules.chat.dto.SessionVO;
import com.lawai.legalassistant.modules.chat.dto.UpdateSessionRequest;
import com.lawai.legalassistant.modules.chat.entity.ChatSession;
import com.lawai.legalassistant.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 对话 Controller
 * <p>
 * 提供会话 CRUD、消息历史、流式问答（SSE）与导出接口。
 * 路径前缀 /api/v1/sessions，对应设计方案 4.6 节 API 列表。
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 会话列表
     */
    @GetMapping
    public Result<List<SessionVO>> list() {
        Long userId = requireLogin();
        return Result.success(chatService.listSessions(userId));
    }

    /**
     * 新建会话
     */
    @PostMapping
    public Result<SessionVO> create(@RequestBody(required = false) CreateSessionRequest req) {
        Long userId = requireLogin();
        ChatSession session = chatService.createSession(userId, req != null ? req.getTitle() : null);
        SessionVO vo = new SessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setStarred(session.getStarred());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return Result.success(vo);
    }

    /**
     * 更新会话（重命名/收藏）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody(required = false) UpdateSessionRequest req) {
        Long userId = requireLogin();
        String title = req != null ? req.getTitle() : null;
        Boolean starred = req != null ? req.getStarred() : null;
        chatService.updateSession(userId, id, title, starred);
        return Result.success();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = requireLogin();
        chatService.deleteSession(userId, id);
        return Result.success();
    }

    /**
     * 批量删除会话
     */
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@Valid @RequestBody BatchDeleteRequest req) {
        Long userId = requireLogin();
        chatService.deleteSessions(userId, req.getIds());
        return Result.success();
    }

    /**
     * 消息历史
     */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(chatService.listMessages(userId, id));
    }

    /**
     * 发送消息（异步模式）
     * <p>
     * 同步保存用户消息后立即返回用户消息 ID，AI 调用在后台异步执行。
     * 前端通过轮询 GET /sessions/{id}/messages 获取 AI 回复。
     */
    @PostMapping("/{id}/messages")
    public Result<Long> sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        Long userId = requireLogin();
        return Result.success(chatService.sendMessageAsync(userId, id, req.getContent()));
    }

    /**
     * 发送图片消息（AI 识别图片中的法律问题并回答）
     * <p>
     * 异步模式：上传图片后立即返回用户消息 ID，
     * 后台异步进行图片识别 + 法律问答，前端轮询获取结果。
     */
    @PostMapping("/{id}/messages/image")
    public Result<Long> sendMessageWithImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Long userId = requireLogin();
        if (file.isEmpty()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "请上传图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "仅支持图片文件");
        }
        try {
            byte[] imageBytes = file.getBytes();
            return Result.success(chatService.sendMessageWithImage(userId, id, imageBytes, contentType));
        } catch (IOException e) {
            throw BusinessException.of(ResultCode.UNKNOWN, "文件读取失败");
        }
    }

    /**
     * SSE 流式问答
     * <p>
     * 发送消息并通过 Server-Sent Events 流式返回 AI 回复。
     * 事件类型：
     * - citations: RAG 引用来源（JSON）
     * - chunk: AI 回复文本片段
     * - done: 流结束标记 [DONE]
     * - error: 错误信息
     *
     * @param id      会话 ID
     * @param req     消息请求体
     * @return SSE 流
     */
    @PostMapping(value = "/{id}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        Long userId = requireLogin();
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> log.debug("SSE 连接关闭: sessionId={}", id));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: sessionId={}", id);
            emitter.complete();
        });
        emitter.onError(ex -> log.error("SSE 连接错误: sessionId={}", id, ex));
        chatService.streamChat(userId, id, req.getContent(), emitter);
        return emitter;
    }

    /**
     * 导出会话为 Markdown
     */
    @PostMapping("/{id}/export")
    public Result<String> export(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(chatService.exportSession(userId, id));
    }

    /**
     * 获取当前登录用户 ID，未登录抛业务异常
     */
    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
