package com.lawai.legalassistant.config;

import com.lawai.legalassistant.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
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
                .cors(cors -> cors.disable()) // CORS 由 CorsFilter Bean 处理
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // SSE 流式问答接口放行：登录态由 ChatService 内部通过 SSE error 事件返回，
                    // 避免 Spring Security 拒绝时因 produces=text/event-stream 内容协商无法返回 403 JSON
                    // 导致空响应/挂起。JwtAuthenticationFilter 仍会解析 token 注入 SecurityContext。
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/messages").permitAll();
                    permitPaths.forEach(p -> auth.requestMatchers(p).permitAll());
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
