package com.lawai.legalassistant.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * B.AI 客户端（OpenAI V1 协议）
 * <p>
 * B.AI（https://chat.b.ai/key）为多模型聚合平台，兼容 OpenAI V1 Chat Completions。
 * 按项目要求作为主模型：配置了 B_AI_API_KEY 即自动启用；由 AiRouter 负责 主(B.AI)→GLM→Agnes 三级降级。
 * 模型默认为本站免费模型，可通过 lawai.ai.bai.model 覆盖。
 */
@Component
public class BaiClient {

    private static final Logger log = LoggerFactory.getLogger(BaiClient.class);

    private final RestClient restClient;
    private final WebClient streamClient;
    private final String model;

    public BaiClient(
            @Value("${lawai.ai.bai.base-url:https://api.b.ai/v1}") String baseUrl,
            @Value("${lawai.ai.bai.api-key:}") String apiKey,
            @Value("${lawai.ai.bai.model:}") String model,
            @Value("${lawai.ai.bai.timeout:25}") long timeoutSeconds) {
        this.model = model != null && !model.isBlank() ? model : "qwen3.8-flash";
        // 独立连接与读取超时，防止挂起耗尽线程池
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout((int) (timeoutSeconds * 1000));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", "text/event-stream")
                .build();
        this.streamClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("BaiClient 已启用 | base-url={} | model={} | timeout={}s", baseUrl, this.model, timeoutSeconds);
    }

    public String modelName() {
        return model;
    }

    public String chat(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();
        // v1.16：Render→api.b.ai 瞬时抖动时重试1次，提升稳定性，避免直接落入 GLM/Agnes
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ChatRequest req = new ChatRequest(
                        model,
                        List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userMessage)),
                        0.3, 4096);
                ChatResponse resp = restClient.post()
                        .uri("/chat/completions")
                        .body(req)
                        .retrieve()
                        .body(ChatResponse.class);
                if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                    throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "B.AI 返回空结果");
                }
                String text = resp.choices().get(0).message().content();
                log.info("B.AI 同步调用成功 | 耗时={}ms | model={}", System.currentTimeMillis() - start, model);
                return text;
            } catch (BusinessException e) {
                // 业务逻辑错误（如"返回空结果"）不重试，直接抛出交由路由降级
                throw e;
            } catch (Exception e) {
                log.warn("B.AI 同步调用失败(第{}次) | 耗时={}ms | error={}", attempt + 1, System.currentTimeMillis() - start, e.getMessage());
                if (attempt == 1) {
                    throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "B.AI AI 服务暂时不可用", e);
                }
            }
        }
        throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "B.AI AI 服务暂时不可用");
    }

    public Flux<String> streamChat(String systemPrompt, String userMessage) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", model);
        req.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)));
        req.put("temperature", 0.3);
        req.put("max_tokens", 4096);
        req.put("stream", true);

        return streamClient.post()
                .uri("/chat/completions")
                .bodyValue(req)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .timeout(Duration.ofSeconds(12)) // 流若无数据超过12s则中断，快速交路由层降级 GLM/Agnes，避免卡顿
                .retry(1) // v1.16：Render→api.b.ai 链路偶发瞬时抖动，重试1次自愈，避免直接落入 GLM/Agnes 导致"暂时不可用"
                .mapNotNull(e -> e == null ? null : e.data())
                .takeWhile(data -> data != null && !"[DONE]".equals(data))
                .map(data -> extractDeltaContent((String) data))
                .filter(Objects::nonNull);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String extractDeltaContent(String data) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(data);
            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (Exception e) {
            return null;
        }
    }

    public String chatWithImage(String systemPrompt, String userMessage, byte[] imageBytes, String mimeType) {
        long start = System.currentTimeMillis();
        try {
            String dataUrl = "data:" + mimeType + ";base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> userContent = Map.of(
                    "role", "user",
                    "content", List.of(
                            Map.of("type", "text", "text", userMessage),
                            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))));
            Map<String, Object> reqBody = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "system", "content", systemPrompt), userContent),
                    "temperature", 0.3,
                    "max_tokens", 4096);
            ChatResponse resp = restClient.post()
                    .uri("/chat/completions")
                    .body(reqBody)
                    .retrieve()
                    .body(ChatResponse.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "B.AI 图片识别返回空结果");
            }
            String text = resp.choices().get(0).message().content();
            log.info("B.AI 图片识别成功 | 耗时={}ms", System.currentTimeMillis() - start);
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("B.AI 图片识别失败 | error={}", e.getMessage());
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "B.AI AI 图片识别服务暂时不可用", e);
        }
    }

    record ChatRequest(String model, List<ChatMessage> messages, double temperature,
                       @JsonProperty("max_tokens") int maxTokens) {}

    record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(String id, String model, List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(int index, ChatMessage message, @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") int promptTokens,
                 @JsonProperty("completion_tokens") int completionTokens,
                 @JsonProperty("total_tokens") int totalTokens) {}
}