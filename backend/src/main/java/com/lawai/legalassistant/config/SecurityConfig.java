package com.lawai.legalassistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * M1 阶段：仅放行白名单路径，其余暂放行（M2 引入 JWT 过滤器后再收紧）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${lawai.security.permit-paths}")
    private List<String> permitPaths;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    permitPaths.forEach(p -> auth.requestMatchers(p).permitAll());
                    // M1 阶段：其余路径暂放行，便于联调；M2 将改为 .anyRequest().authenticated()
                    auth.anyRequest().permitAll();
                });
        // M2 将在此处 addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        return http.build();
    }
}
