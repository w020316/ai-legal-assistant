package com.lawai.legalassistant.modules.chat.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.chat.dto.CreateSessionRequest;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;
import com.lawai.legalassistant.modules.chat.dto.SendMessageRequest;
import com.lawai.legalassistant.modules.chat.dto.SessionVO;
import com.lawai.legalassistant.modules.chat.dto.UpdateSessionRequest;
import com.lawai.legalassistant.modules.chat.entity.ChatSession;
import com.lawai.legalassistant.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
     * 消息历史
     */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(chatService.listMessages(userId, id));
    }

    /**
     * 发送消息（SSE 流式输出）
     * <p>
     * 事件类型：token / citations / done / error，对应设计方案 4.5 节。
     * 注意：错误通过 SSE error 事件返回，不抛异常以免与 text/event-stream 内容协商冲突。
     */
    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        // 登录态在 Service 内通过 SSE error 事件处理，避免内容协商问题
        Long userId = SecurityUtil.getCurrentUserId();
        return chatService.sendMessage(userId, id, req.getContent());
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
