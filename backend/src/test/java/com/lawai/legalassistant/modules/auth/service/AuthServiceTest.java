package com.lawai.legalassistant.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.auth.dto.AuthResponse;
import com.lawai.legalassistant.modules.auth.dto.LoginRequest;
import com.lawai.legalassistant.modules.auth.dto.RegisterRequest;
import com.lawai.legalassistant.modules.auth.entity.SysUser;
import com.lawai.legalassistant.modules.auth.mapper.SysUserMapper;
import com.lawai.legalassistant.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link AuthService} 单元测试（v1.11.0 新增）
 * <p>
 * 覆盖注册、登录、刷新、登出全流程，重点验证：
 * - v1.11.0 修复 H-5：@Transactional 包裹 + DuplicateKeyException 兜底
 * - v1.11.0 修复 C-4：登出 token 加入黑名单
 * - 安全边界：用户名/邮箱重复、密码错误、账号禁用、token 失效
 */
@DisplayName("AuthService 认证服务")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private JwtUtil realJwtUtil;

    @BeforeEach
    void setUp() {
        // 构造一个真实的 JwtUtil 用于 refresh/logout 测试
        realJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(realJwtUtil, "secret",
                "test-secret-key-for-unit-test-must-be-long-enough-32bytes");
        ReflectionTestUtils.setField(realJwtUtil, "accessExpiration", 7200000L);
        ReflectionTestUtils.setField(realJwtUtil, "refreshExpiration", 604800000L);
    }

    private SysUser buildUser(Long id, String username, String role, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("encoded-password");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    @Nested
    @DisplayName("register：注册")
    class Register {

        @Test
        @DisplayName("正常注册成功")
        void registerSuccess() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("password123");
            req.setEmail("new@example.com");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
            doAnswer(invocation -> {
                SysUser u = invocation.getArgument(0);
                u.setId(1L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));

            authService.register(req);

            verify(userMapper).insert(any(SysUser.class));
            verify(auditService).record(eq(1L), eq("REGISTER"), isNull(), anyString());
        }

        @Test
        @DisplayName("用户名已存在抛 CONFLICT")
        void usernameExists() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("existing");
            req.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名已存在");
            verify(userMapper, never()).insert(any(SysUser.class));
        }

        @Test
        @DisplayName("邮箱已被注册抛 CONFLICT")
        void emailExists() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("password123");
            req.setEmail("used@example.com");

            // 第一次 selectCount（用户名）返回 0，第二次（邮箱）返回 1
            when(userMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邮箱已被注册");
            verify(userMapper, never()).insert(any(SysUser.class));
        }

        @Test
        @DisplayName("无邮箱时跳过邮箱校验")
        void registerWithoutEmail() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("password123");
            // email 为 null

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            doAnswer(inv -> {
                ((SysUser) inv.getArgument(0)).setId(1L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));

            authService.register(req);

            // 仅调用一次 selectCount（用户名校验）
            verify(userMapper, times(1)).selectCount(any());
        }

        @Test
        @DisplayName("v1.11.0 H-5：DB 唯一索引冲突兜底")
        void duplicateKeyException() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("concurrent");
            req.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            doThrow(new DuplicateKeyException("duplicate"))
                    .when(userMapper).insert(any(SysUser.class));

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或邮箱已被注册");
        }

        @Test
        @DisplayName("注册后用户角色默认 LAWYER，状态默认 1")
        void registerDefaultRoleAndStatus() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setId(1L);
                // 校验写入的角色与状态
                assertThat(u.getRole()).isEqualTo("LAWYER");
                assertThat(u.getStatus()).isEqualTo(1);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));

            authService.register(req);
        }
    }

    @Nested
    @DisplayName("login：登录")
    class Login {

        @Test
        @DisplayName("正常登录成功")
        void loginSuccess() {
            LoginRequest req = new LoginRequest();
            req.setUsername("alice");
            req.setPassword("password123");

            SysUser user = buildUser(1L, "alice", "LAWYER", 1);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
            when(jwtUtil.generateAccessToken(1L, "alice", "LAWYER")).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken(1L, "alice", "LAWYER")).thenReturn("refresh-token");
            when(jwtUtil.getAccessExpiration()).thenReturn(7200000L);

            AuthResponse resp = authService.login(req, "192.168.1.1");

            assertThat(resp.getAccessToken()).isEqualTo("access-token");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(resp.getUsername()).isEqualTo("alice");
            assertThat(resp.getRole()).isEqualTo("LAWYER");
            assertThat(resp.getExpiresIn()).isEqualTo(7200L);
            verify(auditService).record(eq(1L), eq("LOGIN"), eq("192.168.1.1"), anyString());
        }

        @Test
        @DisplayName("用户不存在抛 UNAUTHORIZED")
        void userNotFound() {
            LoginRequest req = new LoginRequest();
            req.setUsername("ghost");
            req.setPassword("password123");

            when(userMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> authService.login(req, "1.1.1.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或密码错误");
            verify(auditService, never()).record(anyLong(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("密码错误抛 UNAUTHORIZED")
        void wrongPassword() {
            LoginRequest req = new LoginRequest();
            req.setUsername("alice");
            req.setPassword("wrong");

            SysUser user = buildUser(1L, "alice", "LAWYER", 1);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req, "1.1.1.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        @Test
        @DisplayName("账号被禁用抛 FORBIDDEN")
        void accountDisabled() {
            LoginRequest req = new LoginRequest();
            req.setUsername("alice");
            req.setPassword("password123");

            SysUser user = buildUser(1L, "alice", "LAWYER", 0);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req, "1.1.1.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号已被禁用");
        }

        @Test
        @DisplayName("status 为 null 时视为正常账号")
        void nullStatusAllowed() {
            LoginRequest req = new LoginRequest();
            req.setUsername("alice");
            req.setPassword("password123");

            SysUser user = buildUser(1L, "alice", "LAWYER", null);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("a");
            when(jwtUtil.generateRefreshToken(anyLong(), anyString(), anyString())).thenReturn("r");
            when(jwtUtil.getAccessExpiration()).thenReturn(7200000L);

            AuthResponse resp = authService.login(req, null);
            assertThat(resp).isNotNull();
        }
    }

    @Nested
    @DisplayName("refresh：刷新 token")
    class Refresh {

        @Test
        @DisplayName("正常刷新返回新 token")
        void refreshSuccess() {
            String oldRefresh = realJwtUtil.generateRefreshToken(1L, "alice", "LAWYER");
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // 使用真实 JwtUtil 替代 mock
            AuthService svcWithReal = new AuthService(userMapper, passwordEncoder, realJwtUtil, redisTemplate, auditService);
            AuthResponse resp = svcWithReal.refresh(oldRefresh);

            assertThat(resp.getAccessToken()).isNotBlank();
            assertThat(resp.getRefreshToken()).isNotBlank();
            assertThat(resp.getUsername()).isEqualTo("alice");
            assertThat(resp.getRole()).isEqualTo("LAWYER");
            // 旧 token 加入黑名单
            verify(redisTemplate).opsForValue();
            verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("无效 refresh token 抛 UNAUTHORIZED")
        void invalidRefresh() {
            assertThatThrownBy(() -> authService.refresh("invalid-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("refreshToken 无效或已过期");
        }

        @Test
        @DisplayName("access token 不能用于刷新")
        void accessTokenCannotRefresh() {
            String access = realJwtUtil.generateAccessToken(1L, "alice", "LAWYER");
            AuthService svc = new AuthService(userMapper, passwordEncoder, realJwtUtil, redisTemplate, auditService);

            assertThatThrownBy(() -> svc.refresh(access))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("refreshToken 无效或已过期");
        }

        @Test
        @DisplayName("已黑名单的 refresh token 拒绝刷新")
        void blacklistedRefresh() {
            String refresh = realJwtUtil.generateRefreshToken(1L, "alice", "LAWYER");
            AuthService svc = new AuthService(userMapper, passwordEncoder, realJwtUtil, redisTemplate, auditService);
            when(redisTemplate.hasKey(anyString())).thenReturn(true);

            assertThatThrownBy(() -> svc.refresh(refresh))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("refreshToken 已失效");
        }
    }

    @Nested
    @DisplayName("logout：登出")
    class Logout {

        @Test
        @DisplayName("登出时 access 与 refresh 都加入黑名单")
        void logoutBothTokens() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            authService.logout("access", "refresh", 1L);

            verify(valueOperations, times(2)).set(anyString(), eq("1"), any(Duration.class));
            verify(auditService).record(eq(1L), eq("LOGOUT"), isNull(), isNull());
        }

        @Test
        @DisplayName("access 为 null 时仅处理 refresh")
        void logoutNullAccess() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            authService.logout(null, "refresh", 1L);

            verify(valueOperations, times(1)).set(anyString(), eq("1"), any(Duration.class));
            verify(auditService).record(eq(1L), eq("LOGOUT"), isNull(), isNull());
        }

        @Test
        @DisplayName("refresh 为 null 时仅处理 access")
        void logoutNullRefresh() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            authService.logout("access", null, 1L);

            verify(valueOperations, times(1)).set(anyString(), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("userId 为 null 时不记录审计日志")
        void logoutNullUserId() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            authService.logout("access", "refresh", null);

            verify(auditService, never()).record(anyLong(), anyString(), any(), any());
        }

        @Test
        @DisplayName("Redis 写入失败 fail-open：登出不抛异常且仍记录 LOGOUT 审计")
        void logoutRedisFailure() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("redis down"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            // v1.16 起登出黑名单为 fail-open：Redis(Upstash) 抖动不再阻塞登出（登出的关键是前端清 token+跳转），
            // 故登出应正常返回而非抛异常；LOGOUT 审计仍应记录。
            assertThatCode(() -> authService.logout("access", null, 1L))
                    .doesNotThrowAnyException();

            verify(auditService).record(eq(1L), eq("LOGOUT"), any(), any());
        }
    }
}
