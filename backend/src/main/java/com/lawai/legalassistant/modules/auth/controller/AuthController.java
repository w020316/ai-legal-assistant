package com.lawai.legalassistant.modules.auth.controller;

import com.lawai.legalassistant.common.result.Result;
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
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
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
        // refresh token 由请求体传入（可选）
        authService.logout(accessToken, null);
        return Result.success();
    }
}
