package com.lawai.legalassistant.modules.auth.dto;

/**
 * 登出请求
 * <p>
 * v1.17：登出时透传 refreshToken，使服务端可将其加入黑名单，避免被盗的
 * refresh token 在登出后仍可换取新 access token。字段可空——旧客户端不带
 * body 时仍能登出（仅吊销 access token）。
 */
public class LogoutRequest {

    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
