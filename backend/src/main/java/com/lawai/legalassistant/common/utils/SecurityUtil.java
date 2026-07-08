package com.lawai.legalassistant.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 * <p>
 * 从 SecurityContext 获取当前登录用户 ID。
 * M1/M2 阶段 JWT 未接入时返回 null；JWT 过滤器接入后自动生效。
 * <p>
 * 约定：认证后 Authentication.getPrincipal() 应为用户 ID（Long 或可解析为 Long 的字符串）。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID，未登录或无法识别时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }
        if (principal instanceof Number n) {
            return n.longValue();
        }
        if (principal instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
