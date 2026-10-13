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
            try {
                String r = baiClient.chat(systemPrompt, userMessage);
                if (isNotBlank(r)) return r;
                log.warn("B.AI 返回空白，降级 GLM/Agnes");
            } catch (Exception e) { log.warn("B.AI 失败，降级 GLM/Agnes | error={}", e.getMessage()); }
        }
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try {
                String r = tacklekeyClient.chat(systemPrompt, userMessage);
                if (isNotBlank(r)) return r;
                log.warn("GLM 返回空白，降级 Agnes");
            } catch (Exception e) { log.warn("GLM 失败，降级 Agnes | error={}", e.getMessage()); }
        }
        return agnesClient.chat(systemPrompt, userMessage);
    }

    /** 带图片同步对话：B.AI → GLM → Agnes */
    public String chatWithImage(String systemPrompt, String userMessage, byte[] imageBytes, String mimeType) {
        if (baiEnabled && baiClient != null) {
            try {
                String r = baiClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
                if (isNotBlank(r)) return r;
                log.warn("B.AI 图片返回空白，降级 GLM/Agnes");
            } catch (Exception e) { log.warn("B.AI 图片失败，降级 GLM/Agnes | error={}", e.getMessage()); }
        }
        if (tacklekeyEnabled && tacklekeyClient != null) {
            try {
                String r = tacklekeyClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
                if (isNotBlank(r)) return r;
                log.warn("GLM 图片返回空白，降级 Agnes");
            } catch (Exception e) { log.warn("GLM 图片失败，降级 Agnes | error={}", e.getMessage()); }
        }
        return agnesClient.chatWithImage(systemPrompt, userMessage, imageBytes, mimeType);
    }

    /** 返回文本非空白才算有效，否则交由路由层继续降级（防止空答案被当作成功返回并落库） */
    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 流式对话：B.AI → GLM → Agnes（逐级切入，见 {@link #withStreamFallback}） */
    public reactor.core.publisher.Flux<String> streamChat(String systemPrompt, String userMessage) {
        // 低层链路：Agnes（base）
        reactor.core.publisher.Flux<String> stream = agnesClient.streamChat(systemPrompt, userMessage);
        if (tacklekeyEnabled && tacklekeyClient != null) {
            final reactor.core.publisher.Flux<String> agnesFallback = stream;
            stream = withStreamFallback(tacklekeyClient.streamChat(systemPrompt, userMessage), agnesFallback, "GLM");
            log.debug("流式对话加入 GLM 层 | model={}", tacklekeyClient.modelName());
        }
        if (baiEnabled && baiClient != null) {
            final reactor.core.publisher.Flux<String> lower = stream;
            stream = withStreamFallback(baiClient.streamChat(systemPrompt, userMessage), lower, "B.AI");
            log.debug("流式对话走主模型(B.AI) | model={}", baiClient.modelName());
        }
        return stream;
    }

    /**
     * 流式降级包装（v1.18，修复审查 V1-05；v1.18 阶段二·B-06 修正判据）。
     * <ul>
     *   <li>防重放：主层已输出<b>有效内容</b>后中段失败（网络断/包间隔超时/5xx）时<b>不降级</b>——
     *       下层从头重发会让前端出现双份/三份文本且落库内容重复；仅当尚未输出有效内容时才切换下层；</li>
     *   <li>防空答：主层正常完成但累计内容为空白（零 chunk 或纯空白）时切换下层，
     *       与同步链 isNotBlank 守卫语义对齐，防止空答案直达前端并落库。</li>
     * </ul>
     * v1.18 阶段二·B-06：判据由"是否发过任意 chunk"（emitted）改为"累计内容是否非空白"——
     * 纯空白流此前被 emitted=true 锁定而不降级，与同步链不一致；改用 concatWith 在正常完成后
     * 再按累计内容判定，可覆盖"零 chunk"与"纯空白"两种干净完成场景（switchIfEmpty 只能覆盖前者）。
     */
    private reactor.core.publisher.Flux<String> withStreamFallback(
            reactor.core.publisher.Flux<String> primary,
            reactor.core.publisher.Flux<String> fallback,
            String label) {
        final StringBuilder acc = new StringBuilder();
        final java.util.concurrent.atomic.AtomicBoolean degraded = new java.util.concurrent.atomic.AtomicBoolean(false);
        return primary
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        acc.append(chunk);
                    }
                })
                .onErrorResume(e -> {
                    if (!acc.toString().isBlank()) {
                        log.warn("{} 流式已输出有效内容，中段失败不再降级重放 | error={}", label, e.getMessage());
                        return reactor.core.publisher.Flux.error(e);
                    }
                    log.warn("{} 流式失败(未输出有效内容)，降级 | error={}", label, e.getMessage());
                    degraded.set(true);
                    return fallback;
                })
                .concatWith(reactor.core.publisher.Flux.defer(() -> {
                    if (degraded.get()) {
                        // 已在错误分支降级，避免重复订阅下层
                        return reactor.core.publisher.Flux.empty();
                    }
                    if (acc.toString().isBlank()) {
                        log.warn("{} 流式返回空白内容(零 chunk 或纯空白)，降级", label);
                        degraded.set(true);
                        return fallback;
                    }
                    return reactor.core.publisher.Flux.empty();
                }));
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