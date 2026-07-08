package com.lawai.legalassistant.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

/**
 * 消息视图
 * <p>
 * citations 为原始 JSON 字符串，使用 @JsonRawValue 直接输出为 JSON，
 * 避免被二次转义；为 null 时输出 null。
 */
public class MessageVO {

    private Long id;

    private String role;

    private String content;

    /** 引用来源，原始 JSON（@JsonRawValue 原样输出） */
    @JsonRawValue
    private String citations;

    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
