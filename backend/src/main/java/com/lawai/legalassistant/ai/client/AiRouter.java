package com.lawai.legalassistant.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AI 模型路由层
 * <p>
 * 优先调用 Tacklekey（免费模型 openai/gpt-5.5:free），失败时自动降级到 Agnes（付费稳定）。
 * 通过 lawai.ai.tacklekey.enabled 控制是否启用 Tacklekey，默认 false（仅用 Agnes）。
 * <p>
 * 降级触发条件：TacklekeyClient 抛出任何异常（包括 429 配额耗尽、超时、网络错误）。
 * 降级后记录 WARN 日志，不影响用户体验。
 */
@Component
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final AgnesClient agnesClient;
    private final TacklekeyClient tacklekeyClient; // 可能为 null（未启用时）
    private final boolean tacklekeyEnabled;

    @Autowired
    public AiRouter(
            AgnesClient agnesClient,
            @Value("${lawai.ai.tacklekey.enabled:false}") boolean tacklekeyEnabled,
            Optional<TacklekeyClient> tacklekeyClientOptional
    ) {
        this.agnesClient = agnesClient;
        this.tacklekeyEnabled = tacklekeyEnabled;
        this.tacklekeyClient = tacklekeyClientOptional.orElse(null);
        log.info("AiRouter 初始化 | tacklekey.enabled={} | tacklekeyClient={}", tacklekeyEnabled, tacklekeyClient != null ? "已加载" : "未加载");
    }

    /**
     * 同步对话：优先 Tacklekey，降级 Agnes
     */
    public String chat(String systemPrompt, String userMessage) {
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try {
                return tacklekeyClient.chat(systemPrompt, userMessage);
            } catch (Exception e) {
                log.warn("Tacklekey 调用失败，降级到 Agnes | error={}", e.getMessage());
            }
        }
        return agnesClient.chat(systemPrompt, userMessage);
    }

    /**
     * 带图片的同步对话：优先 Tacklekey，降级 Agnes
     */
    public String chatWithImage(String systemPrompt, String userMessage, byte[] imageBytes, String mimeType) {
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try {
                return tacklekeyClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
            } catch (Exception e) {
                log.warn("Tacklekey 图片识别失败，降级到 Agnes | error={}", e.getMessage());
            }
        }
        return agnesClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
    }

    /**
     * 流式对话：当前仅 Agnes 支持（Tacklekey 流式未实现，避免复杂度）
     */
    public reactor.core.publisher.Flux<String> streamChat(String systemPrompt, String userMessage) {
        return agnesClient.streamChat(systemPrompt, userMessage);
    }

    /**
     * 文本向量化：仅 Agnes 支持（Tacklekey 无 embedding 模型）
     */
    public float[] embed(String text) {
        return agnesClient.embed(text);
    }

    /**
     * 批量向量化：仅 Agnes 支持
     */
    public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        return agnesClient.embedBatch(texts);
    }
}
