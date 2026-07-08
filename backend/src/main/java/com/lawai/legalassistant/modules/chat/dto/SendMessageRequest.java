package com.lawai.legalassistant.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息请求
 */
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
