package com.lawai.legalassistant.modules.chat.controller;

import com.lawai.legalassistant.common.exception.GlobalExceptionHandler;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;

import com.lawai.legalassistant.modules.chat.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ChatController} 单元测试（v1.18 新增，补覆盖率洼地）。
 * <p>
 * standalone MockMvc + mock ChatService，覆盖会话 CRUD/消息/导出/未登录语义。
 * SSE 流式端点依赖真实 emitter 生命周期，由 ChatServiceTest.Stream 锁定行为。
 */
@DisplayName("ChatController 会话接口")
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /sessions：返回会话列表")
    void list() throws Exception {
        when(chatService.listSessions(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /sessions：有 body 与无 body 均可创建")
    void create() throws Exception {
        com.lawai.legalassistant.modules.chat.entity.ChatSession vo =
                new com.lawai.legalassistant.modules.chat.entity.ChatSession();
        when(chatService.createSession(eq(42L), eq("合同咨询"))).thenReturn(vo);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"合同咨询\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        when(chatService.createSession(eq(42L), isNull())).thenReturn(vo);
        mockMvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("PUT/DELETE /sessions/{id}：透传 userId 与参数")
    void updateAndDelete() throws Exception {
        mockMvc.perform(put("/api/v1/sessions/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名\"}"))
                .andExpect(status().isOk());
        verify(chatService).updateSession(42L, 5L, "改名", null);

        mockMvc.perform(delete("/api/v1/sessions/5"))
                .andExpect(status().isOk());
        verify(chatService).deleteSession(42L, 5L);
    }

    @Test
    @DisplayName("DELETE /sessions/batch：批量删除")
    void deleteBatch() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,3]}"))
                .andExpect(status().isOk());
        verify(chatService).deleteSessions(eq(42L), any());
    }

    @Test
    @DisplayName("GET /sessions/{id}/messages 与 POST 导出")
    void messagesAndExport() throws Exception {
        when(chatService.listMessages(42L, 5L)).thenReturn(List.of(new MessageVO()));
        when(chatService.exportSession(42L, 5L)).thenReturn("# 会话导出");

        mockMvc.perform(get("/api/v1/sessions/5/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/sessions/5/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("# 会话导出"));
    }

    @Test
    @DisplayName("POST /sessions/{id}/messages：发送并返回用户消息 ID")
    void sendMessage() throws Exception {
        when(chatService.sendMessageAsync(42L, 5L, "你好")).thenReturn(100L);

        mockMvc.perform(post("/api/v1/sessions/5/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    @DisplayName("未登录：经全局异常处理器返回业务码 1002")
    void requireLoginBusinessCode() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
