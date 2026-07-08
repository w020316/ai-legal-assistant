package com.lawai.legalassistant.modules.chat.dto;

/**
 * 新建会话请求
 */
public class CreateSessionRequest {

    /** 会话标题，可选 */
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
