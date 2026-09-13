package com.lawai.legalassistant.config;

import com.lawai.legalassistant.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Spring Security 配置
 * <p>
 * JWT 无状态鉴权，白名单路径放行，其余需认证。方法级安全已启用，支持 @PreAuthorize。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAsync
public class SecurityConfig {

    @Value("${lawai.security.permit-paths}")
    private List<String> permitPaths;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable()) // CORS 响应头由独立 CorsFilter Bean 提供
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // v1.13.1：放行 OPTIONS 预检，避免跨域 POST 被浏览器 CORS 预检 403 →
                    // 前端报"网络异常，请检查连接"（如登录/refresh/创建会话等）
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    permitPaths.forEach(p -> auth.requestMatchers(p).permitAll());
                    auth.anyRequest().authenticated();
                })
                // v1.18：显式 401 入口——此前未认证请求走 Spring 默认 Http403ForbiddenEntryPoint 返回 403，
                // 前端仅对 401/1002 触发自动刷新 token，导致 access 过期后用户只会看到"无权限访问"
                // 且 7 天 refreshToken 形同虚设。现返回与业务层 requireLogin 一致的 1002/401 结构化体，
                // 让前端拦截器走统一的"静默续期→重放请求"链路。
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":1002,\"message\":\"登录已过期，请重新登录\",\"data\":null}");
                }))
                // v1.18（审查 P2#20）：安全响应头——Spring 默认已含 X-Content-Type-Options/
                // X-Frame-Options，补 Referrer-Policy 与 Permissions-Policy；API 域不加 CSP
                // （Swagger UI 需要内联脚本），前端 CSP 由 Cloudflare Pages 的 public/_headers 提供
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
