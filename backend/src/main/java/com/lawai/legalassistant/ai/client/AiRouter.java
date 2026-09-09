package com.lawai.legalassistant.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AI 模型路由层（三级降级链）
 * <p>
 * 优先级：主模型 = ① B.AI 免费模型（配置了 lawai.ai.bai.api-key 即启用）
 *         → ② GLM 官方免费模型（TacklekeyClient，配置了 lawai.ai.glm.api-key 即启用）
 *         → ③ Agnes（兜底）。
 * 各层任一失败（网络/429/超时/空结果）自动移交下一层，保证问答不中断。
 * <p>
 * 向量化（embed/embedBatch）仅 Agnes 支持。
 */
@Component
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final AgnesClient agnesClient;
    private final TacklekeyClient tacklekeyClient; // 可能 null
    private final BaiClient baiClient;             // 可能 null
    private final boolean baiEnabled;
    private final boolean tacklekeyEnabled;

    @Autowired
    public AiRouter(
            AgnesClient agnesClient,
            Optional<BaiClient> baiClientOptional,
            Optional<TacklekeyClient> tacklekeyClientOptional,
            @Value("${lawai.ai.bai.api-key:}") String baiApiKey,
            @Value("${lawai.ai.glm.enabled:false}") boolean glmEnabledFlag,
            @Value("${lawai.ai.glm.api-key:}") String glmApiKey) {
        this.agnesClient = agnesClient;
        this.baiClient = baiClientOptional.orElse(null);
        this.tacklekeyClient = tacklekeyClientOptional.orElse(null);
        // 配置了 API Key 即自动启用对应为主模型，无显式开关负担
        this.baiEnabled = baiClient != null && baiApiKey != null && !baiApiKey.isBlank();
        this.tacklekeyEnabled = tacklekeyClient != null
                && (glmEnabledFlag || (glmApiKey != null && !glmApiKey.isBlank()));
        log.info("AiRouter 初始化 | B.AI(主)={} | GLM={} | Agnes(兜底)=true",
                baiEnabled, tacklekeyEnabled);
    }

    /** 同步对话：B.AI → GLM → Agnes */
    public String chat(String systemPrompt, String userMessage) {
        if (baiEnabled && baiClient != null) {
            try { return baiClient.chat(systemPrompt, userMessage); }
            catch (Exception e) { log.warn("B.AI 失败，降级 GLM/Agnes | error={}", e.getMessage()); }
        }
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try { return tacklekeyClient.chat(systemPrompt, userMessage); }
            catch (Exception e) { log.warn("GLM 失败，降级 Agnes | error={}", e.getMessage()); }
        }
        return agnesClient.chat(systemPrompt, userMessage);
    }

    /** 带图片同步对话：B.AI → GLM → Agnes */
    public String chatWithImage(String systemPrompt, String userMessage, byte[] imageBytes, String mimeType) {
        if (baiEnabled && baiClient != null) {
            try { return baiClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType); }
            catch (Exception e) { log.warn("B.AI 图片失败，降级 GLM/Agnes | error={}", e.getMessage()); }
        }
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try { return tacklekeyClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType); }
            catch (Exception e) { log.warn("GLM 图片失败，降级 Agnes | error={}", e.getMessage()); }
        }
        return agnesClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
    }

    /** 流式对话：B.AI → GLM → Agnes（onErrorResume 逐级切入） */
    public reactor.core.publisher.Flux<String> streamChat(String systemPrompt, String userMessage) {
        // 低层链路：Agnes（base）
        reactor.core.publisher.Flux<String> stream = agnesClient.streamChat(systemPrompt, userMessage);
        if (tacklekeyEnabled && tacklekeyClient != null) {
            final reactor.core.publisher.Flux<String> fallback = stream;
            stream = tacklekeyClient.streamChat(systemPrompt, userMessage)
                    .onErrorResume(e -> { log.warn("GLM 流式失败，降级 Agnes | error={}", e.getMessage()); return fallback; });
            log.info("流式对话加入 GLM 层 | model={}", tacklekeyClient.modelName());
        }
        if (baiEnabled && baiClient != null) {
            final reactor.core.publisher.Flux<String> lower = stream;
            stream = baiClient.streamChat(systemPrompt, userMessage)
                    .onErrorResume(e -> { log.warn("B.AI 流式失败，降级 GLM/Agnes | error={}", e.getMessage()); return lower; });
            log.info("流式对话走主模型(B.AI) | model={}", baiClient.modelName());
        }
        return stream;
    }

    /** 文本向量化：仅 Agnes 支持 */
    public float[] embed(String text) {
        return agnesClient.embed(text);
    }

    /** 批量向量化：仅 Agnes 支持 */
    public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        return agnesClient.embedBatch(texts);
    }
}