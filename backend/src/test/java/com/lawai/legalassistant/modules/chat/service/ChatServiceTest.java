package com.lawai.legalassistant.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.compliance.SensitiveWordFilter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.chat.dto.MessageVO;
import com.lawai.legalassistant.modules.chat.dto.SessionVO;
import com.lawai.legalassistant.modules.chat.entity.ChatMessage;
import com.lawai.legalassistant.modules.chat.entity.ChatSession;
import com.lawai.legalassistant.modules.chat.mapper.ChatMessageMapper;
import com.lawai.legalassistant.modules.chat.mapper.ChatSessionMapper;
import com.lawai.legalassistant.modules.rag.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * {@link ChatService} 单元测试（v1.11.0 新增）
 * <p>
 * 覆盖同步方法：createSession / listSessions / getSession / updateSession /
 * deleteSession / deleteSessions / listMessages / exportSession。
 * 异步方法（sendMessageAsync / streamChat）因涉及线程池与超时，单独验证参数校验路径。
 */
@DisplayName("ChatService 对话服务")
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private RagService ragService;
    @Mock
    private AiRouter aiRouter;
    @Mock
    private SensitiveWordFilter sensitiveWordFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(sessionMapper, messageMapper, ragService,
                aiRouter, objectMapper, sensitiveWordFilter);
    }

    private ChatSession buildSession(Long id, Long userId, String title) {
        ChatSession s = new ChatSession();
        s.setId(id);
        s.setUserId(userId);
        s.setTitle(title);
        s.setStarred(false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    private ChatMessage buildMessage(Long id, Long sessionId, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(Instant.now());
        return m;
    }

    @Nested
    @DisplayName("createSession：新建会话")
    class CreateSession {

        @Test
        @DisplayName("带标题正常创建")
        void createWithTitle() {
            when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(inv -> {
                ((ChatSession) inv.getArgument(0)).setId(1L);
                return 1;
            });

            ChatSession s = chatService.createSession(100L, "法律咨询");

            assertThat(s.getUserId()).isEqualTo(100L);
            assertThat(s.getTitle()).isEqualTo("法律咨询");
            assertThat(s.getStarred()).isFalse();
        }

        @Test
        @DisplayName("null 标题使用默认 '新对话'")
        void createWithNullTitle() {
            when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(inv -> {
                ((ChatSession) inv.getArgument(0)).setId(1L);
                return 1;
            });

            ChatSession s = chatService.createSession(1L, null);
            assertThat(s.getTitle()).isEqualTo("新对话");
        }

        @Test
        @DisplayName("空白标题使用默认 '新对话'")
        void createWithBlankTitle() {
            when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(inv -> {
                ((ChatSession) inv.getArgument(0)).setId(1L);
                return 1;
            });

            ChatSession s = chatService.createSession(1L, "   ");
            assertThat(s.getTitle()).isEqualTo("新对话");
        }
    }

    @Nested
    @DisplayName("getSession：获取会话（含归属校验）")
    class GetSession {

        @Test
        @DisplayName("归属正确返回会话")
        void ownSession() {
            ChatSession s = buildSession(1L, 100L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            ChatSession result = chatService.getSession(100L, 1L);
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("会话不存在抛 NOT_FOUND")
        void sessionNotFound() {
            when(sessionMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> chatService.getSession(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("会话不存在或无权限");
        }

        @Test
        @DisplayName("会话不属于该用户抛 NOT_FOUND")
        void notOwnSession() {
            ChatSession s = buildSession(1L, 200L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            assertThatThrownBy(() -> chatService.getSession(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("会话不存在或无权限");
        }

        @Test
        @DisplayName("userId 为 null 时抛 UNAUTHORIZED")
        void nullUserId() {
            // v1.11.0 修复后：userId 为 null 在 selectById 调用前就抛 UNAUTHORIZED
            assertThatThrownBy(() -> chatService.getSession(null, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("请先登录");
        }
    }

    @Nested
    @DisplayName("updateSession：更新会话")
    class UpdateSession {

        @Test
        @DisplayName("更新标题")
        void updateTitle() {
            ChatSession s = buildSession(1L, 100L, "old");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            chatService.updateSession(100L, 1L, "new title", null);

            ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
            verify(sessionMapper).updateById(captor.capture());
            ChatSession updated = captor.getValue();
            assertThat(updated.getTitle()).isEqualTo("new title");
            assertThat(updated.getStarred()).isNull();
        }

        @Test
        @DisplayName("更新收藏状态")
        void updateStarred() {
            ChatSession s = buildSession(1L, 100L, "title");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            chatService.updateSession(100L, 1L, null, true);

            ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
            verify(sessionMapper).updateById(captor.capture());
            ChatSession updated = captor.getValue();
            assertThat(updated.getTitle()).isNull();
            assertThat(updated.getStarred()).isTrue();
        }

        @Test
        @DisplayName("归属错误抛异常且不更新")
        void updateNotOwn() {
            ChatSession s = buildSession(1L, 200L, "title");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            assertThatThrownBy(() -> chatService.updateSession(100L, 1L, "new", null))
                    .isInstanceOf(BusinessException.class);
            verify(sessionMapper, never()).updateById(any(ChatSession.class));
        }
    }

    @Nested
    @DisplayName("deleteSession：删除会话")
    class DeleteSession {

        @Test
        @DisplayName("归属正确时删除")
        void deleteOwn() {
            ChatSession s = buildSession(1L, 100L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            chatService.deleteSession(100L, 1L);

            verify(sessionMapper).deleteById(1L);
        }

        @Test
        @DisplayName("归属错误时拒绝删除")
        void deleteNotOwn() {
            ChatSession s = buildSession(1L, 200L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            assertThatThrownBy(() -> chatService.deleteSession(100L, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(sessionMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("deleteSessions：批量删除会话（v1.11.0 H-8）")
    class DeleteSessions {

        @Test
        @DisplayName("空列表直接返回不查询")
        void emptyList() {
            chatService.deleteSessions(100L, List.of());
            verify(sessionMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("null 列表直接返回")
        void nullList() {
            chatService.deleteSessions(100L, null);
            verify(sessionMapper, never()).selectList(any());
        }

        // 注：deleteOwned / noOwnedSessions 两个分支需要 LambdaQueryWrapper 的 TableInfo 缓存，
        // 纯 Mockito 单元测试无法初始化该缓存（需 Spring 上下文 + MyBatis-Plus 启动处理），
        // 这两个分支由集成测试覆盖（参见 ChatServiceIT）。
    }

    @Nested
    @DisplayName("listMessages：消息历史")
    class ListMessages {

        @Test
        @DisplayName("按时间正序返回消息列表")
        void listMessagesOrdered() {
            ChatSession s = buildSession(1L, 100L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);
            List<ChatMessage> msgs = List.of(
                    buildMessage(1L, 1L, "user", "问题"),
                    buildMessage(2L, 1L, "assistant", "回答")
            );
            when(messageMapper.selectList(any())).thenReturn(msgs);

            List<MessageVO> result = chatService.listMessages(100L, 1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRole()).isEqualTo("user");
            assertThat(result.get(0).getContent()).isEqualTo("问题");
            assertThat(result.get(1).getRole()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("归属错误抛异常")
        void listMessagesNotOwn() {
            ChatSession s = buildSession(1L, 200L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            assertThatThrownBy(() -> chatService.listMessages(100L, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(messageMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("空消息列表返回空集合")
        void emptyMessages() {
            ChatSession s = buildSession(1L, 100L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);
            when(messageMapper.selectList(any())).thenReturn(List.of());

            List<MessageVO> result = chatService.listMessages(100L, 1L);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("exportSession：导出会话")
    class ExportSession {

        @Test
        @DisplayName("导出 Markdown 包含标题与消息")
        void exportMarkdown() {
            ChatSession s = buildSession(1L, 100L, "法律咨询");
            when(sessionMapper.selectById(1L)).thenReturn(s);
            List<ChatMessage> msgs = List.of(
                    buildMessage(1L, 1L, "user", "什么是违约金？"),
                    buildMessage(2L, 1L, "assistant", "违约金是...")
            );
            when(messageMapper.selectList(any())).thenReturn(msgs);

            String md = chatService.exportSession(100L, 1L);

            assertThat(md).startsWith("# 法律咨询");
            assertThat(md).contains("用户");
            assertThat(md).contains("AI 助手");
            assertThat(md).contains("什么是违约金？");
            assertThat(md).contains("违约金是...");
        }

        @Test
        @DisplayName("归属错误抛异常")
        void exportNotOwn() {
            ChatSession s = buildSession(1L, 200L, "test");
            when(sessionMapper.selectById(1L)).thenReturn(s);

            assertThatThrownBy(() -> chatService.exportSession(100L, 1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("sendMessageAsync：参数校验")
    class SendMessageAsyncValidation {

        @Test
        @DisplayName("userId 为 null 抛 UNAUTHORIZED")
        void nullUserId() {
            assertThatThrownBy(() -> chatService.sendMessageAsync(null, 1L, "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("请先登录");
        }

        @Test
        @DisplayName("内容为 null 抛 PARAM_ERROR")
        void nullContent() {
            assertThatThrownBy(() -> chatService.sendMessageAsync(1L, 1L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("消息内容不能为空");
        }

        @Test
        @DisplayName("内容为空白抛 PARAM_ERROR")
        void blankContent() {
            assertThatThrownBy(() -> chatService.sendMessageAsync(1L, 1L, "   "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("消息内容不能为空");
        }

        @Test
        @DisplayName("命中敏感词抛 PARAM_ERROR")
        void sensitiveContent() {
            when(sensitiveWordFilter.contains("赌博")).thenReturn(true);

            assertThatThrownBy(() -> chatService.sendMessageAsync(1L, 1L, "赌博"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("消息包含敏感内容");
        }
    }

    @Test
    @DisplayName("listSessions：空会话列表返回空集合")
    void listSessionsEmpty() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        List<SessionVO> result = chatService.listSessions(100L);
        assertThat(result).isEmpty();
    }

    @Nested
    @DisplayName("streamChat：SSE 流式问答（v1.18 流式加固行为锁定）")
    class Stream {

        private final org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                org.mockito.Mockito.mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);

        private ChatSession stubSession() {
            ChatSession s = new ChatSession();
            s.setId(9L);
            s.setUserId(1L);
            s.setTitle("新对话");
            when(sessionMapper.selectById(9L)).thenReturn(s);
            return s;
        }

        @Test
        @DisplayName("v1.18：未登录不发空流，先发 error 事件再 complete")
        void streamValidationFailureSendsErrorEvent() throws Exception {
            chatService.streamChat(null, 9L, "你好", emitter);

            verify(emitter, atLeastOnce()).send(any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any(Throwable.class));
        }

        @Test
        @DisplayName("v1.18：流式正常完成——完整内容落库并发 done 事件")
        void streamSuccessSavesAndCompletes() throws Exception {
            stubSession();
            when(sensitiveWordFilter.contains("你好")).thenReturn(false);
            when(sensitiveWordFilter.filter("你好")).thenReturn("你好");
            when(aiRouter.streamChat(any(), any())).thenReturn(reactor.core.publisher.Flux.just("你好", "世界"));

            chatService.streamChat(1L, 9L, "你好", emitter);

            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            // 用户消息(同步) + 助手消息(流式完成) 共 2 次 insert；getValue 取最后一次 = 助手消息
            verify(messageMapper, timeout(3000).atLeast(2)).insertMessage(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getRole()).isEqualTo("assistant");
            assertThat(msgCaptor.getValue().getContent()).isEqualTo("你好世界");
            verify(emitter, timeout(3000)).complete();
        }

        @Test
        @DisplayName("v1.18：流式超时裁决——半截内容落库、发 error 事件并 dispose 上游")
        void streamTimeoutSavesPartialAndSendsError() throws Exception {
            stubSession();
            when(sensitiveWordFilter.contains("写一篇长文")).thenReturn(false);
            when(sensitiveWordFilter.filter("写一篇长文")).thenReturn("写一篇长文");
            // chunk 送达（doOnNext 已执行、fullContent 已累积）后再触发超时，消除异步时序竞态
            java.util.concurrent.CountDownLatch chunkDelivered = new java.util.concurrent.CountDownLatch(1);
            when(aiRouter.streamChat(any(), any())).thenReturn(reactor.core.publisher.Flux.<String>create(sink -> {
                sink.next("前半段。");
                chunkDelivered.countDown();
                // 保持打开：模拟上游挂起不结束
            }));

            chatService.streamChat(1L, 9L, "写一篇长文", emitter);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter, timeout(3000).atLeastOnce()).onTimeout(timeoutCaptor.capture());
            assertThat(chunkDelivered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // 模拟容器触发 emitter 超时（155s）
            timeoutCaptor.getValue().run();

            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(messageMapper, timeout(3000).atLeast(2)).insertMessage(msgCaptor.capture());
            ChatMessage saved = msgCaptor.getValue();
            assertThat(saved.getRole()).isEqualTo("assistant");
            assertThat(saved.getContent()).startsWith("前半段。");
            assertThat(saved.getContent()).contains("超时中断");
            verify(emitter, atLeastOnce()).complete();
        }
    }

    @Nested
    @DisplayName("sendMessageAsync：异步问答（v1.18 补覆盖）")
    class SendMessageAsyncHappyPath {

        @Test
        @DisplayName("成功路径：用户消息落库 + AI 回复落库 + 自动命名触发")
        void asyncSuccessSavesAssistantAndRenames() {
            ChatSession s = new ChatSession();
            s.setId(9L);
            s.setUserId(1L);
            s.setTitle("新对话");
            when(sessionMapper.selectById(9L)).thenReturn(s);
            when(sensitiveWordFilter.contains("试用期违约金合法吗")).thenReturn(false);
            when(sensitiveWordFilter.filter("试用期违约金合法吗")).thenReturn("试用期违约金合法吗");
            when(aiRouter.chat(anyString(), anyString())).thenReturn("法律回答");
            doAnswer(inv -> {
                ChatMessage m = inv.getArgument(0);
                m.setId(101L);
                return 1;
            }).when(messageMapper).insertMessage(any(ChatMessage.class));

            Long userMsgId = chatService.sendMessageAsync(1L, 9L, "试用期违约金合法吗");

            assertThat(userMsgId).isNotNull();

            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            // 用户消息(同步) + 助手消息(异步) 共 2 次 insert；getValue 取最后一次 = 助手消息
            verify(messageMapper, timeout(3000).atLeast(2)).insertMessage(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getRole()).isEqualTo("assistant");
            assertThat(msgCaptor.getValue().getContent()).isEqualTo("法律回答");

            // 标题为"新对话" → 触发自动命名（同步 AI 调用，等其完成）
            verify(sessionMapper, timeout(3000).atLeast(2)).updateById(any(ChatSession.class));
        }
    }

    @Nested
    @DisplayName("sendMessageWithImage：图片问答（v1.18 补覆盖）")
    class SendMessageWithImagePath {

        @Test
        @DisplayName("成功路径：识别结果过敏感词后更新用户消息并回答")
        void imageSuccess() {
            ChatSession s = new ChatSession();
            s.setId(9L);
            s.setUserId(1L);
            s.setTitle("新对话");
            when(sessionMapper.selectById(9L)).thenReturn(s);
            when(aiRouter.chatWithImage(anyString(), anyString(), any(), anyString()))
                    .thenReturn("图中问题是租房纠纷");
            when(sensitiveWordFilter.contains("图中问题是租房纠纷")).thenReturn(false);
            when(sensitiveWordFilter.filter("图中问题是租房纠纷")).thenReturn("图中问题是租房纠纷");
            when(aiRouter.chat(anyString(), anyString())).thenReturn("图片回答");
            doAnswer(inv -> {
                ChatMessage m = inv.getArgument(0);
                m.setId(77L);
                return 1;
            }).when(messageMapper).insertMessage(any(ChatMessage.class));

            Long id = chatService.sendMessageWithImage(1L, 9L, new byte[]{1, 2, 3}, "image/png");

            assertThat(id).isEqualTo(77L);
            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            // 占位用户消息 + 助手消息 共 2 次 insert；getValue 取最后一次 = 助手消息
            verify(messageMapper, timeout(3000).atLeast(2)).insertMessage(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getRole()).isEqualTo("assistant");
            assertThat(msgCaptor.getValue().getContent()).isEqualTo("图片回答");
        }

        @Test
        @DisplayName("v1.18 P2#7：识别结果命中敏感词时终止并提示")
        void imageSensitiveBlocked() {
            ChatSession s = new ChatSession();
            s.setId(9L);
            s.setUserId(1L);
            s.setTitle("新对话");
            when(sessionMapper.selectById(9L)).thenReturn(s);
            when(aiRouter.chatWithImage(anyString(), anyString(), any(), anyString())).thenReturn("违规内容");
            when(sensitiveWordFilter.contains("违规内容")).thenReturn(true);
            doAnswer(inv -> {
                ChatMessage m = inv.getArgument(0);
                m.setId(78L);
                return 1;
            }).when(messageMapper).insertMessage(any(ChatMessage.class));

            chatService.sendMessageWithImage(1L, 9L, new byte[]{1}, "image/png");

            // 用户消息改为"未通过安全校验"占位，并保存提示助手消息；不再进入 RAG/AI 问答
            verify(messageMapper, timeout(3000).atLeastOnce()).updateById(any(ChatMessage.class));
            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(messageMapper, timeout(3000).atLeast(2)).insertMessage(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getContent()).contains("未通过安全校验");
            verify(aiRouter, timeout(3000).times(1)).chatWithImage(anyString(), anyString(), any(), anyString());
            verify(aiRouter, never()).chat(anyString(), anyString());
        }
    }
}
