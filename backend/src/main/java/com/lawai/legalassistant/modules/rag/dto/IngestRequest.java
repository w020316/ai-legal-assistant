package com.lawai.legalassistant.modules.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 文档入库请求
 */
public class IngestRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "文档类型不能为空")
    private String docType;

    private String source;

    @NotBlank(message = "原文内容不能为空")
    private String rawText;

    /** 归属类型：PUBLIC/PRIVATE，默认 PUBLIC */
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
