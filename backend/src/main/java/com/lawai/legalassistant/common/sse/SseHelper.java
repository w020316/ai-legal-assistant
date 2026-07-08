package com.lawai.legalassistant.common.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 工具类
 * <p>
 * 封装 SseEmitter 创建与事件发送，事件类型：token / citations / done / error。
 * 对应设计方案 4.5 节 SSE 流式协议。
 */
public final class SseHelper {

    private static final Logger log = LoggerFactory.getLogger(SseHelper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SSE 超时时间（毫秒），300s */
    public static final long TIMEOUT = 300_000L;

    public static final String EVENT_TOKEN = "token";
    public static final String EVENT_CITATIONS = "citations";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_ERROR = "error";

    private SseHelper() {
    }

    /**
     * 创建 SseEmitter，并注册回调处理
     *
     * @return 配置好的 SseEmitter
     */
    public static SseEmitter create() {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.error("SSE 连接异常", ex);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * 发送事件
     *
     * @param emitter   SSE 发射器
     * @param eventName 事件名（token/citations/done/error）
     * @param data      数据对象（将序列化为 JSON）
     */
    public static void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(toJson(data)));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE 发送事件失败: event={}, err={}", eventName, e.getMessage());
        }
    }

    /**
     * 安全关闭 SseEmitter
     *
     * @param emitter SSE 发射器
     */
    public static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常: {}", e.getMessage());
        }
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("SSE 数据序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
