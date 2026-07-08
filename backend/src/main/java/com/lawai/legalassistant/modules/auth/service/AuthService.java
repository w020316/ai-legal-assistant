package com.lawai.legalassistant.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.auth.entity.SysUser;
import com.lawai.legalassistant.modules.auth.mapper.SysUserMapper;
import com.lawai.legalassistant.modules.auth.dto.AuthResponse;
import com.lawai.legalassistant.modules.auth.dto.LoginRequest;
import com.lawai.legalassistant.modules.auth.dto.RegisterRequest;
import com.lawai.legalassistant.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 认证服务
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    /** refresh token 黑名单 Redis key 前缀 */
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    /** 黑名单保留时长（与 refresh 过期一致） */
    private static final Duration BLACKLIST_TTL = Duration.ofDays(7);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       StringRedisTemplate redisTemplate, AuditService auditService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    /**
     * 注册
     */
    public void register(RegisterRequest req) {
        // 校验用户名唯一
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "用户名已存在");
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            Long emailExists = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, req.getEmail()));
            if (emailExists != null && emailExists > 0) {
                throw BusinessException.of(ResultCode.CONFLICT, "邮箱已被注册");
            }
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("LAWYER");
        user.setStatus(1);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userMapper.insert(user);
        log.info("用户注册成功: {}", req.getUsername());
    }

    /**
     * 登录
     */
    public AuthResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BusinessException.of(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        auditService.record(user.getId(), "LOGIN", null, null);
        log.info("用户登录: {}", user.getUsername());
        return new AuthResponse(access, refresh, jwtUtil.getAccessExpiration() / 1000,
                user.getUsername(), user.getRole());
    }

    /**
     * 刷新 Token
     */
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken, JwtUtil.TYPE_REFRESH)) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        }
        // 检查黑名单
        if (isBlacklisted(refreshToken)) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "refreshToken 已失效");
        }
        Claims claims = jwtUtil.parse(refreshToken);
        Long userId = jwtUtil.getUserId(claims);
        String username = jwtUtil.getUsername(claims);
        String role = jwtUtil.getRole(claims);
        // 旧 refresh 加入黑名单
        blacklist(refreshToken);
        String access = jwtUtil.generateAccessToken(userId, username, role);
        String newRefresh = jwtUtil.generateRefreshToken(userId, username, role);
        return new AuthResponse(access, newRefresh, jwtUtil.getAccessExpiration() / 1000, username, role);
    }

    /**
     * 登出：将 access/refresh 加入黑名单
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            blacklist(accessToken);
        }
        if (refreshToken != null) {
            blacklist(refreshToken);
        }
    }

    private void blacklist(String token) {
        try {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", BLACKLIST_TTL);
        } catch (Exception e) {
            log.warn("Redis 黑名单写入失败，降级忽略: {}", e.getMessage());
        }
    }

    private boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            log.warn("Redis 黑名单查询失败，降级放行: {}", e.getMessage());
            return false;
        }
    }
}
