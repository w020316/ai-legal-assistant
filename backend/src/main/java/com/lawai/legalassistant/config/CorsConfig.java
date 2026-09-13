package com.lawai.legalassistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 跨域配置
 * <p>
 * 由独立 CorsFilter 提供跨域响应头；结合 SecurityConfig 对 OPTIONS 预检的放行，
 * 确保跨域 POST（登录/refresh/创建会话）的预检不被安全链拦截为 403。
 */
@Configuration
public class CorsConfig {

    @Value("${lawai.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${lawai.cors.allowed-methods}")
    private List<String> allowedMethods;

    @Value("${lawai.cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${lawai.cors.max-age}")
    private long maxAge;

    /**
     * v1.18（审查 P2#21）：显式置为最高优先级——此前该 Filter 未设 @Order，注册在
     * Security 过滤链（order -100）之后，预检放行实际依赖 SecurityConfig 的 OPTIONS
     * permitAll 兜底，逻辑分散脆弱；置于最高优先级后 CORS 处理独立且先于安全链。
     */
    @Bean
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(allowedMethods);
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
