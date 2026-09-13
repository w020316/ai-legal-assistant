package com.lawai.legalassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Agnes(Spring AI OpenAI 兼容客户端) HTTP 超时配置（v1.18，修复审查 V1-08）。
 * <p>
 * Spring AI 自动装配的 RestClient/WebClient 默认<b>无连接/读取超时</b>：Agnes 是三级降级链
 * 的兜底层（同步 chat 的终点、embed 在 RAG 深处），一旦上游挂起，aiExecutor 的 8 个线程会被
 * 逐一永久占用，队列(100)打满后 CallerRunsPolicy 把 AI 调用推回 Tomcat 线程 → 全站阻塞。
 * 外层 orTimeout(150s) 只保证用户拿到超时提示，<b>不能回收线程</b>。
 * <p>
 * 覆盖 Boot 默认的 {@code RestClient.Builder}/{@code WebClient.Builder} prototype bean
 * （Spring AI OpenAI 自动装配即从二者构建），全工程无其他使用方（v1.18 审查已核实）。
 * BaiClient/TacklekeyClient 自建工厂、自带超时，不受影响。
 */
@Configuration
public class AiHttpTimeoutConfig {

    /** 同步链路（chat/chatWithImage/embed）：连接 10s，读超时 120s（覆盖慢答案，仍可被外层 150s 兜底） */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * 流式链路（streamChat）：连接 10s，响应读取间隔超时 60s——Agnes 无自身空闲超时，
     * 此前挂死流只能等 SSE emitter 155s 兜底；60s 无新数据即断流，由 AiRouter 降级语义收尾。
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
