package com.lawai.legalassistant.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求
 */
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    // v1.18（审查 P2#9）：此前仅 @NotBlank——前端 2000 字只是 UI 约束，超长内容可直通
    // 敏感词过滤、历史拼接与 AI 调用。上限 4000 为前端 2000 的 2 倍余量（含过滤后膨胀）。
    @Size(max = 4000, message = "消息内容不能超过 4000 字")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
