package com.lawai.legalassistant.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiRouter} 路由决策单元测试（v1.13.0 新增）
 * <p>
 * 验证三级降级链：B.AI → GLM(Tacklekey) → Agnes。
 * 构造签名：new AiRouter(agnesClient, Optional<BaiClient>, Optional<TacklekeyClient>,
 *                         baiApiKey, glmEnabledFlag, glmApiKey)
 */
@DisplayName("AiRouter 路由决策")
@ExtendWith(MockitoExtension.class)
class AiRouterTest {

    @Mock
    private AgnesClient agnesClient;
    @Mock
    private TacklekeyClient tacklekeyClient;
    @Mock
    private BaiClient baiClient;

    private static final String SYSTEM = "sys";
    private static final String USER = "user";

    @Test
    @DisplayName("未开启且无 Key：仅走 Agnes")
    void disabledWithoutKey() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", false, "");
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("agnes");
        verify(tacklekeyClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("未开启 GLM 但已配置 GLM Key：自动以 GLM 为主")
    void autoEnabledWhenKeyPresent() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", false, "tk-key-123");
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
        verify(agnesClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("显式开启 GLM：以 GLM 为主")
    void explicitlyEnabled() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", true, "");
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
    }

    @Test
    @DisplayName("配置 BAI ApiKey：以 B.AI 为主模型(第一优先级)")
    void baiIsPrimaryWhenKeyConfigured() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenReturn("bai");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("bai");
        verify(tacklekeyClient, never()).chat(SYSTEM, USER);
        verify(agnesClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("B.AI 失败：降级 GLM")
    void baiFailureDegradesToGlm() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenThrow(new RuntimeException("bai down"));
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
        verify(agnesClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("B.AI 与 GLM 均失败：降级 Agnes")
    void allPrimaryFailDegradesToAgnes() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenThrow(new RuntimeException("bai down"));
        when(tacklekeyClient.chat(SYSTEM, USER)).thenThrow(new RuntimeException("glm quota"));
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("agnes");
    }

    @Test
    @DisplayName("未配置 B.AI：退化为 GLM/Agnes")
    void noBaiKeyFallsBackToGlm() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "", true, "glm-key");
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
        verify(baiClient, never()).chat(SYSTEM, USER);
    }

    // ===== v1.17：空/空白响应必须降级，不得当作成功返回 =====

    @Test
    @DisplayName("B.AI 返回空字符串：降级 GLM(不被当作成功)")
    void blankBaiResponseDegradesToGlm() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenReturn("");
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
        verify(agnesClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("B.AI 返回 null：降级 GLM")
    void nullBaiResponseDegradesToGlm() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenReturn(null);
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
    }

    @Test
    @DisplayName("B.AI 与 GLM 均返回空白：降级 Agnes")
    void blankPrimariesDegradeToAgnes() {
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chat(SYSTEM, USER)).thenReturn("   ");
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("");
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("agnes");
    }

    @Test
    @DisplayName("图片：B.AI 返回空白降级 GLM，GLM 空白降级 Agnes")
    void blankImageResponseDegrades() {
        byte[] img = new byte[]{1, 2, 3};
        AiRouter router = new AiRouter(agnesClient, Optional.of(baiClient), Optional.of(tacklekeyClient), "bai-key-1", true, "glm-key");
        when(baiClient.chatWithImage(SYSTEM, USER, img, "image/png")).thenReturn("");
        when(tacklekeyClient.chatWithImage(SYSTEM, USER, img, "image/png")).thenReturn("glm-img");

        String answer = router.chatWithImage(SYSTEM, USER, img, "image/png");

        assertThat(answer).isEqualTo("glm-img");
        verify(agnesClient, never()).chatWithImage(SYSTEM, USER, img, "image/png");
    }

    @Test
    @DisplayName("v1.18：流式中段失败(已发chunk)不降级重放，错误向下游传播")
    void streamMidwayFailureDoesNotReplay() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", false, "tk-key");
        when(tacklekeyClient.streamChat(SYSTEM, USER)).thenReturn(
                reactor.core.publisher.Flux.just("前半段")
                        .concatWith(reactor.core.publisher.Flux.error(new RuntimeException("midway boom"))));
        // AiRouter 构建降级链时会急切调用 agnesClient.streamChat 生成冷 Flux，
        // 因此用"是否被订阅"而非 verify(never) 判断是否真的降级
        java.util.concurrent.atomic.AtomicBoolean agnesSubscribed = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(agnesClient.streamChat(SYSTEM, USER)).thenReturn(
                reactor.core.publisher.Flux.<String>never().doOnSubscribe(s -> agnesSubscribed.set(true)));

        java.util.List<String> seen = new java.util.ArrayList<>();
        router.streamChat(SYSTEM, USER)
                .doOnNext(seen::add)
                .onErrorComplete()
                .blockLast();

        // 已发出"前半段"后失败：chunk 正常到达下游，且 Agnes 不得被订阅从头重发（否则前端出现重复内容）
        assertThat(seen).containsExactly("前半段");
        assertThat(agnesSubscribed.get()).isFalse();
    }

    @Test
    @DisplayName("v1.18：流式首包前失败降级 Agnes")
    void streamFailureBeforeFirstChunkDegrades() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", false, "tk-key");
        when(tacklekeyClient.streamChat(SYSTEM, USER)).thenReturn(reactor.core.publisher.Flux.error(new RuntimeException("boom")));
        when(agnesClient.streamChat(SYSTEM, USER)).thenReturn(reactor.core.publisher.Flux.just("agnes-a", "agnes-b"));

        assertThat(router.streamChat(SYSTEM, USER).collectList().block()).containsExactly("agnes-a", "agnes-b");
    }

    @Test
    @DisplayName("v1.18：流式空完成(200+0 chunk)降级 Agnes，防空答落库")
    void streamEmptyCompletionDegrades() {
        AiRouter router = new AiRouter(agnesClient, Optional.empty(), Optional.of(tacklekeyClient), "", false, "tk-key");
        when(tacklekeyClient.streamChat(SYSTEM, USER)).thenReturn(reactor.core.publisher.Flux.empty());
        when(agnesClient.streamChat(SYSTEM, USER)).thenReturn(reactor.core.publisher.Flux.just("agnes"));

        assertThat(router.streamChat(SYSTEM, USER).collectList().block()).containsExactly("agnes");
    }
}