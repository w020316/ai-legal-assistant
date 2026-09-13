package com.lawai.legalassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link JwtAuthenticationFilter} 单元测试（v1.18 新增，补覆盖率洼地）。
 * <p>
 * 覆盖：无/非 Bearer 头放行、合法 access token 注入认证、refresh 类型拒绝、
 * 黑名单拒绝、Redis 故障 fail-open 放行。
 */
@DisplayName("JwtAuthenticationFilter 认证过滤器")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-for-unit-test-must-be-long-enough-32bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 7200000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);
        filter = new JwtAuthenticationFilter(jwtUtil, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter() throws Exception {
        filter.doFilterInternal(request, response, chain);
    }

    @Test
    @DisplayName("无 Authorization 头：直接放行且不注入认证")
    void noHeaderContinues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        doFilter();
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("非 Bearer 头：放行且不注入认证")
    void nonBearerContinues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");
        doFilter();
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("合法 access token：注入 userId 认证")
    void validAccessTokenAuthenticates() throws Exception {
        String token = jwtUtil.generateAccessToken(42L, "alice", "ADMIN");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        doFilter();

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("refresh 类型 token 不能作为 access 使用")
    void refreshTokenRejected() throws Exception {
        String token = jwtUtil.generateRefreshToken(1L, "alice", "LAWYER");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("黑名单中的 token：拒绝（fail-closed）")
    void blacklistedTokenRejected() throws Exception {
        String token = jwtUtil.generateAccessToken(1L, "alice", "LAWYER");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Redis 故障：fail-open 放行（保可用性）")
    void redisFailureFailsOpen() throws Exception {
        String token = jwtUtil.generateAccessToken(1L, "alice", "LAWYER");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        doFilter();

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("伪造/非法 token：拒绝")
    void garbageTokenRejected() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage-token");

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(redisTemplate, never()).hasKey(anyString());
    }
}
