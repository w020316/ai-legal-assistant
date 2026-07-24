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

    /** 条款通俗化解释：用大白话说清这条条款的真实含义和对签署方的影响 */
    private String plainExplanation;

    /** 风险财务估算：量化该高风险条款可能带来的经济损失，如"预计最高损失合同金额 20%" */
    private String financialExposure;

    /** 谈判建议优先级：TIER_1（建议拒绝/重新谈判）/ TIER_2（强烈要求修改）/ TIER_3（建议优化） */
    private String priority;

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

    public String getPlainExplanation() {
        return plainExplanation;
    }

    public void setPlainExplanation(String plainExplanation) {
        this.plainExplanation = plainExplanation;
    }

    public String getFinancialExposure() {
        return financialExposure;
    }

    public void setFinancialExposure(String financialExposure) {
        this.financialExposure = financialExposure;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
