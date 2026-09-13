package com.lawai.legalassistant.modules.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 文档入库请求
 */
public class IngestRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @NotBlank(message = "文档类型不能为空")
    @Pattern(regexp = "LAW|CASE|TEMPLATE|GUIDE|USER_UPLOAD", message = "文档类型不合法")
    private String docType;

    @Size(max = 500, message = "来源不能超过 500 字")
    private String source;

    @NotBlank(message = "原文内容不能为空")
    // v1.18（审查 P2#12）：补长度上限——此前无上限的超大文本会触发切片爆炸（500 片上限截断前的
    // 内存与切片开销）；20 万字符约可切 500 片，与 RagService.MAX_CHUNKS_PER_DOC 对齐
    @Size(max = 200_000, message = "原文内容不能超过 20 万字符")
    private String rawText;

    /** 归属类型：PUBLIC/PRIVATE，默认 PUBLIC */
    @Pattern(regexp = "PUBLIC|PRIVATE", message = "归属类型不合法")
    private String ownerType;

    /** PRIVATE 时为用户 ID */
    private Long ownerId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}
