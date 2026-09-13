package com.lawai.legalassistant.modules.document.dto;

import java.time.Instant;

/**
 * 用户文档列表 VO（v1.18，修复审查 V1-02 / P2#14）
 * <p>
 * 列表接口不回传 ocrText/analysisResult 大字段（ocrText 最大 20MB 全文）；
 * {@code analysisStatus} 由 analysisResult 是否存在派生（completed/pending）——
 * 此前端期望该字段而后端实体从不返回，{@code statusTagType(undefined)} 抛
 * TypeError，用户名下存在任一文档时文档页整体渲染崩溃。
 */
public class UserDocumentVO {

    private Long id;

    private String filename;

    private String fileType;

    private Long fileSize;

    private Long version;

    private Instant createdAt;

    private String analysisStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus) {
        this.analysisStatus = analysisStatus;
    }
}
