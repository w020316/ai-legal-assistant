package com.lawai.legalassistant.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Tacklekey AI 客户端
 * <p>
 * Tacklekey（api.tacklekey.com）兼容 OpenAI V1 协议，提供免费模型 openai/gpt-5.5:free。
 * 本类用 RestClient 独立调用，不依赖 Spring AI 自动配置，与 AgnesClient 完全解耦。
 * 通过 lawai.ai.tacklekey.enabled 环境变量控制是否启用，默认关闭。
 * <p>
 * 设计动机：免费模型配额有限，启用后作为主模型优先调用；配额耗尽时由 AiRouter 自动降级到 Agnes。
 */
@Component
@ConditionalOnProperty(name = "lawai.ai.tacklekey.enabled", havingValue = "true")
public class TacklekeyClient {

    private static final Logger log = LoggerFactory.getLogger(TacklekeyClient.class);

    private final RestClient restClient;
    private final String model;

    public TacklekeyClient(
            @Value("${lawai.ai.tacklekey.base-url:https://api.tacklekey.com}") String baseUrl,
            @Value("${lawai.ai.tacklekey.api-key:}") String apiKey,
            @Value("${lawai.ai.tacklekey.model:openai/gpt-5.5:free}") String model,
            @Value("${lawai.ai.tacklekey.timeout:60}") long timeoutSeconds) {
        this.model = model;
        // 配置连接与读取超时，防止 AI 接口挂起耗尽线程池
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10s 连接超时
        factory.setReadTimeout((int) (timeoutSeconds * 1000)); // 读取超时由配置控制
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("TacklekeyClient 已启用 | base-url={} | model={} | timeout={}s", baseUrl, model, timeoutSeconds);
    }

    /**
     * 同步对话
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复文本
     */
    public String chat(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();
        try {
            ChatRequest req = new ChatRequest(
                    model,
                    List.of(
                            new ChatMessage("system", systemPrompt),
                            new ChatMessage("user", userMessage)
                    ),
                    0.3,
                    4096
            );
            ChatResponse resp = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(req)
                    .retrieve()
                    .body(ChatResponse.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "Tacklekey 返回空结果");
            }
            String text = resp.choices().get(0).message().content();
            log.info("Tacklekey 同步调用成功 | 耗时={}ms | tokens={}", System.currentTimeMillis() - start, resp.usage() != null ? resp.usage().totalTokens() : -1);
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tacklekey 同步调用失败 | 耗时={}ms", System.currentTimeMillis() - start, e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "Tacklekey AI 服务暂时不可用", e);
        }
    }

    /**
     * 带图片的同步对话（多模态）
     * <p>
     * 通过 data URL 传递图片（OpenAI Vision 格式），兼容 OpenAI V1 协议。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param imageBytes   图片字节数组
     * @param mimeType     图片 MIME 类型（如 image/png、image/jpeg）
     * @return AI 回复文本
     */
    public String chatWithImage(String systemPrompt, String userMessage, byte[] imageBytes, String mimeType) {
        long start = System.currentTimeMillis();
        try {
            String dataUrl = "data:" + mimeType + ";base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);
            // OpenAI Vision 多模态消息格式
            Map<String, Object> userContent = Map.of(
                    "role", "user",
                    "content", List.of(
                            Map.of("type", "text", "text", userMessage),
                            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                    )
            );
            Map<String, Object> systemContent = Map.of("role", "system", "content", systemPrompt);
            Map<String, Object> reqBody = Map.of(
                    "model", model,
                    "messages", List.of(systemContent, userContent),
                    "temperature", 0.3,
                    "max_tokens", 4096
            );
            ChatResponse resp = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(reqBody)
                    .retrieve()
                    .body(ChatResponse.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "Tacklekey 图片识别返回空结果");
            }
            String text = resp.choices().get(0).message().content();
            log.info("Tacklekey 图片识别成功 | 耗时={}ms", System.currentTimeMillis() - start);
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tacklekey 图片识别失败 | 耗时={}ms", System.currentTimeMillis() - start, e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "Tacklekey AI 图片识别服务暂时不可用", e);
        }
    }

    // ===== OpenAI V1 协议 DTO =====

    record ChatRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens
    ) {}

    record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(
            String id,
            String model,
            List<Choice> choices,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(int index, ChatMessage message, @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
