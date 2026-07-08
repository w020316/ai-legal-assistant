package com.lawai.legalassistant.modules.chat.dto;

/**
 * 更新会话请求（重命名/收藏）
 * <p>
 * title 与 starred 均可选，仅更新传入字段。
 */
public class UpdateSessionRequest {

    private String title;

    private Boolean starred;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getStarred() {
        return starred;
    }

    public void setStarred(Boolean starred) {
        this.starred = starred;
    }
}
