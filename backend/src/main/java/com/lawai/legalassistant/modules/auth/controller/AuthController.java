package com.lawai.legalassistant.modules.auth.controller;

import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.auth.dto.AuthResponse;
import com.lawai.legalassistant.modules.auth.dto.LoginRequest;
import com.lawai.legalassistant.modules.auth.dto.RefreshRequest;
import com.lawai.legalassistant.modules.auth.dto.RegisterRequest;
import com.lawai.legalassistant.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@Tag(name = "认证", description = "注册/登录/刷新Token/登出")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return Result.success();
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return Result.success(authService.login(req, getClientIp(request)));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.success(authService.refresh(req.getRefreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String accessToken = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        authService.logout(accessToken, null, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    /**
     * 提取客户端真实 IP（v1.7.0 新增，用于审计日志）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
