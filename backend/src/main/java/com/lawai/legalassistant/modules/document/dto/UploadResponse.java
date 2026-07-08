package com.lawai.legalassistant.modules.document.dto;

/**
 * 文档上传响应
 */
public class UploadResponse {

    private Long id;
    private String filename;
    private String fileType;
    private Long fileSize;

    public UploadResponse() {
    }

    public UploadResponse(Long id, String filename, String fileType, Long fileSize) {
        this.id = id;
        this.filename = filename;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

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
}
