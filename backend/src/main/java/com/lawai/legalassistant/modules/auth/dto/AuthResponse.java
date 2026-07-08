package com.lawai.legalassistant.modules.auth.dto;

/**
 * 认证响应
 */
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String username;
    private String role;

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, String username, String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.username = username;
        this.role = role;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
}
