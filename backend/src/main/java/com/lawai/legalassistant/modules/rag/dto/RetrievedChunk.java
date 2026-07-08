package com.lawai.legalassistant.modules.rag.dto;

/**
 * 检索结果片段
 * <p>
 * 用于封装 pgvector 检索返回的切片信息，对应设计方案引用来源结构。
 */
public class RetrievedChunk {

    /** 切片 ID */
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 文档标题 */
    private String title;

    /** 文档来源 */
    private String source;

    /** 切片内容片段 */
    private String snippet;

    /** 相似度得分（0~1，余弦相似度） */
    private Double score;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
