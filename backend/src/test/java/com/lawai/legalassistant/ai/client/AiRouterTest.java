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
 * 验证 GLM(Tacklekey) 自动启用逻辑：
 * - 显式开启 → 以 GLM 为主模型；
 * - 未开启但已配置 TACKLEKEY_API_KEY → 自动以 GLM 为主模型；
 * - 未配置 Key → 仅走 Agnes；
 * - GLM 失败 → 自动降级 Agnes。
 */
@DisplayName("AiRouter 路由决策")
@ExtendWith(MockitoExtension.class)
class AiRouterTest {

    @Mock
    private AgnesClient agnesClient;
    @Mock
    private TacklekeyClient tacklekeyClient;

    private static final String SYSTEM = "sys";
    private static final String USER = "user";

    @Test
    @DisplayName("未开启且无 Key：仅走 Agnes")
    void disabledWithoutKey() {
        AiRouter router = new AiRouter(agnesClient, false, "", Optional.of(tacklekeyClient));
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("agnes");
        verify(tacklekeyClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("未开启但已配置 Key：自动以 GLM 为主")
    void autoEnabledWhenKeyPresent() {
        AiRouter router = new AiRouter(agnesClient, false, "tk-key-123", Optional.of(tacklekeyClient));
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
        verify(agnesClient, never()).chat(SYSTEM, USER);
    }

    @Test
    @DisplayName("显式开启：以 GLM 为主")
    void explicitlyEnabled() {
        AiRouter router = new AiRouter(agnesClient, true, "", Optional.of(tacklekeyClient));
        when(tacklekeyClient.chat(SYSTEM, USER)).thenReturn("glm");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("glm");
    }

    @Test
    @DisplayName("GLM 失败：自动降级 Agnes")
    void gracefulDegradeOnGlmFailure() {
        AiRouter router = new AiRouter(agnesClient, true, "", Optional.of(tacklekeyClient));
        when(tacklekeyClient.chat(SYSTEM, USER)).thenThrow(new RuntimeException("quota exhausted"));
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        String answer = router.chat(SYSTEM, USER);

        assertThat(answer).isEqualTo("agnes");
    }

    @Test
    @DisplayName("未启用客户端为 null：仅走 Agnes")
    void noClientDisablesGlm() {
        AiRouter router = new AiRouter(agnesClient, true, "", Optional.empty());
        when(agnesClient.chat(SYSTEM, USER)).thenReturn("agnes");

        assertThat(router.chat(SYSTEM, USER)).isEqualTo("agnes");
    }
}