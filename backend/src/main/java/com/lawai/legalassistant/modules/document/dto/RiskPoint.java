package com.lawai.legalassistant.modules.document.dto;

/**
 * 风险点
 */
public class RiskPoint {

    /** 条款 */
    private String clause;

    /** 风险等级：高/中/低 */
    private String level;

    /** 问题描述 */
    private String issue;

    /** 修改建议 */
    private String suggestion;

    /** 法律依据 */
    private String legalBasis;

    public String getClause() {
        return clause;
    }

    public void setClause(String clause) {
        this.clause = clause;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis) {
        this.legalBasis = legalBasis;
    }
}
