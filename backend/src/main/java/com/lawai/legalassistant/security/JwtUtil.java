package com.lawai.legalassistant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * <p>
 * access token 有效期 2h，refresh token 7d。
 * claims 含 userId / username / role。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** access token 类型标识 */
    public static final String TYPE_ACCESS = "access";
    /** refresh token 类型标识 */
    public static final String TYPE_REFRESH = "refresh";

    @Value("${lawai.jwt.secret:${JWT_SECRET}}")
    private String secret;

    @Value("${lawai.jwt.access-expiration:7200000}")
    private long accessExpiration;

    @Value("${lawai.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 access token
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, accessExpiration, TYPE_ACCESS);
    }

    /**
     * 生成 refresh token
     */
    public String generateRefreshToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, refreshExpiration, TYPE_REFRESH);
    }

    private String buildToken(Long userId, String username, String role, long expiration, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId,
                        "username", username,
                        "role", role,
                        "type", type
                ))
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token，返回 claims；失败返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 token 是否有效且类型匹配
     */
    public boolean isValid(String token, String expectedType) {
        Claims claims = parse(token);
        if (claims == null) return false;
        if (!expectedType.equals(claims.get("type", String.class))) return false;
        return claims.getExpiration().after(Date.from(Instant.now()));
    }

    public Long getUserId(Claims claims) {
        Object v = claims.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        return null;
    }

    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }
}
