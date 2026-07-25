package com.lawai.legalassistant.modules.export.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导出请求
 * <p>
 * v1.11.0 新增：通用 Markdown 转 Word/PDF 导出请求体。
 */
public class ExportRequest {

    /** 文档标题（用于文件名与首行大标题，可为空） */
    @Size(max = 128, message = "标题长度不能超过 128 字符")
    private String title;

    /** Markdown 正文 */
    @NotBlank(message = "导出内容不能为空")
    @Size(max = 100000, message = "导出内容过长，请控制在 10 万字符以内")
    private String content;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
