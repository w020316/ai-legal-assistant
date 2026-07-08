package com.lawai.legalassistant.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agnes AI 客户端封装
 * <p>
 * Agnes AI 兼容 OpenAI V1 协议，通过 Spring AI 的 OpenAI starter 接入。
 * 本类统一封装同步/流式对话与向量化调用，处理重试与降级。
 */
@Component
public class AgnesClient {

    private static final Logger log = LoggerFactory.getLogger(AgnesClient.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;

    public AgnesClient(ChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 同步对话
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复文本
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            );
            return chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Agnes AI 同步调用失败", e);
            throw new RuntimeException("AI 服务暂时不可用", e);
        }
    }

    /**
     * 流式对话
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 流式响应
     */
    public Flux<String> streamChat(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content()
                .doOnError(e -> log.error("Agnes AI 流式调用失败", e));
    }

    /**
     * 文本向量化
     *
     * @param text 待向量化的文本
     * @return 向量数组
     */
    public float[] embed(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.error("Agnes AI 向量化失败", e);
            throw new RuntimeException("文本向量化失败", e);
        }
    }

    /**
     * 批量向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            return response.getResults().stream()
                    .map(r -> r.getOutput())
                    .toList();
        } catch (Exception e) {
            log.error("Agnes AI 批量向量化失败", e);
            throw new RuntimeException("批量向量化失败", e);
        }
    }
}
