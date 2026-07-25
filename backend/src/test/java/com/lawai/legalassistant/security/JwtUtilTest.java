package com.lawai.legalassistant.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtUtil} 单元测试（v1.11.0 新增）
 * <p>
 * 安全敏感类：测试 access/refresh token 生成、解析、校验、过期、类型混淆。
 * 使用 ReflectionTestUtils 注入 @Value 字段，避免启动 Spring 上下文。
 */
@DisplayName("JwtUtil JWT 工具")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    /** 测试密钥：必须 ≥32 字节以满足 HS256 算法要求 */
    private static final String TEST_SECRET = "test-secret-key-for-unit-test-must-be-long-enough-32bytes";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 7200000L);   // 2 小时
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L); // 7 天
    }

    @Nested
    @DisplayName("生成与解析")
    class GenerateAndParse {

        @Test
        @DisplayName("生成 access token 非空")
        void generateAccessToken() {
            String token = jwtUtil.generateAccessToken(1L, "张三", "LAWYER");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("生成 refresh token 非空")
        void generateRefreshToken() {
            String token = jwtUtil.generateRefreshToken(1L, "张三", "LAWYER");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("access 与 refresh token 不相同")
        void accessAndRefreshDiffer() {
            String access = jwtUtil.generateAccessToken(1L, "u", "r");
            String refresh = jwtUtil.generateRefreshToken(1L, "u", "r");
            assertThat(access).isNotEqualTo(refresh);
        }

        @Test
        @DisplayName("解析 access token 返回正确 claims")
        void parseAccessToken() {
            String token = jwtUtil.generateAccessToken(100L, "alice", "ADMIN");
            Claims claims = jwtUtil.parse(token);
            assertThat(claims).isNotNull();
            assertThat(jwtUtil.getUserId(claims)).isEqualTo(100L);
            assertThat(jwtUtil.getUsername(claims)).isEqualTo("alice");
            assertThat(jwtUtil.getRole(claims)).isEqualTo("ADMIN");
            assertThat(claims.get("type", String.class)).isEqualTo(JwtUtil.TYPE_ACCESS);
        }

        @Test
        @DisplayName("解析 refresh token 类型为 refresh")
        void parseRefreshToken() {
            String token = jwtUtil.generateRefreshToken(2L, "bob", "LAWYER");
            Claims claims = jwtUtil.parse(token);
            assertThat(claims).isNotNull();
            assertThat(claims.get("type", String.class)).isEqualTo(JwtUtil.TYPE_REFRESH);
        }

        @Test
        @DisplayName("subject 等于 userId 字符串")
        void subjectIsUserId() {
            String token = jwtUtil.generateAccessToken(42L, "u", "r");
            Claims claims = jwtUtil.parse(token);
            assertThat(claims.getSubject()).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("isValid：token 校验")
    class IsValid {

        @Test
        @DisplayName("有效 access token 校验通过")
        void validAccessToken() {
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_ACCESS)).isTrue();
        }

        @Test
        @DisplayName("有效 refresh token 校验通过")
        void validRefreshToken() {
            String token = jwtUtil.generateRefreshToken(1L, "u", "r");
            assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_REFRESH)).isTrue();
        }

        @Test
        @DisplayName("access token 不能用作 refresh（类型混淆攻击防御）")
        void accessTokenNotValidAsRefresh() {
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_REFRESH)).isFalse();
        }

        @Test
        @DisplayName("refresh token 不能用作 access")
        void refreshTokenNotValidAsAccess() {
            String token = jwtUtil.generateRefreshToken(1L, "u", "r");
            assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_ACCESS)).isFalse();
        }

        @Test
        @DisplayName("篡改后的 token 校验失败")
        void tamperedToken() {
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertThat(jwtUtil.isValid(tampered, JwtUtil.TYPE_ACCESS)).isFalse();
        }

        @Test
        @DisplayName("空字符串 token 校验失败")
        void emptyToken() {
            assertThat(jwtUtil.isValid("", JwtUtil.TYPE_ACCESS)).isFalse();
        }

        @Test
        @DisplayName("随机字符串 token 校验失败")
        void randomString() {
            assertThat(jwtUtil.isValid("not.a.valid.token", JwtUtil.TYPE_ACCESS)).isFalse();
        }

        @Test
        @DisplayName("过期的 token 校验失败")
        void expiredToken() {
            // 构造已过期 token：expiration 设为 1ms，sleep 10ms 后校验
            ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1L);
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_ACCESS)).isFalse();
        }
    }

    @Nested
    @DisplayName("parse：异常输入")
    class ParseInvalid {

        @Test
        @DisplayName("null 返回 null")
        void parseNull() {
            assertThat(jwtUtil.parse(null)).isNull();
        }

        @Test
        @DisplayName("空字符串返回 null")
        void parseEmpty() {
            assertThat(jwtUtil.parse("")).isNull();
        }

        @Test
        @DisplayName("非 token 字符串返回 null")
        void parseRandom() {
            assertThat(jwtUtil.parse("garbage")).isNull();
        }

        @Test
        @DisplayName("用不同密钥签名的 token 解析失败")
        void parseWithDifferentSecret() {
            // 用原密钥生成
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            // 换密钥后解析应失败
            JwtUtil other = new JwtUtil();
            ReflectionTestUtils.setField(other, "secret", "another-secret-key-with-32-bytes-length-enough-xx");
            ReflectionTestUtils.setField(other, "accessExpiration", 7200000L);
            ReflectionTestUtils.setField(other, "refreshExpiration", 604800000L);
            assertThat(other.parse(token)).isNull();
        }
    }

    @Nested
    @DisplayName("getUserId：类型兼容")
    class GetUserId {

        @Test
        @DisplayName("Long 类型 userId 正确解析")
        void longUserId() {
            String token = jwtUtil.generateAccessToken(Long.MAX_VALUE, "u", "r");
            Claims claims = jwtUtil.parse(token);
            assertThat(jwtUtil.getUserId(claims)).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("小 userId 正确解析")
        void smallUserId() {
            String token = jwtUtil.generateAccessToken(1L, "u", "r");
            Claims claims = jwtUtil.parse(token);
            assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("getAccessExpiration 返回配置值")
    void getAccessExpiration() {
        assertThat(jwtUtil.getAccessExpiration()).isEqualTo(7200000L);
    }
}
