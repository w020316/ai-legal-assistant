package com.lawai.legalassistant.modules.document.dto;

import java.util.List;

/**
 * 文档分析结果
 * <p>
 * 对应 AI 合同审查输出的结构化 JSON，v1.4.3 起新增：
 * 安全评分、风险分级、条款通俗化总览、缺失条款提醒。
 */
public class DocumentAnalysisVO {

    /** 整体摘要 */
    private String summary;

    /** 安全评分（0-100，越高越安全） */
    private Integer score;

    /** 整体风险等级：高/中/低 */
    private String riskLevel;

    /** 条款通俗化总览：用大白话解释整份合同的核心约定与立场偏向 */
    private String plainSummary;

    /** 风险点列表 */
    private List<RiskPoint> riskPoints;

    /** 缺失条款提醒列表 */
    private List<MissingClause> missingClauses;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getPlainSummary() {
        return plainSummary;
    }

    public void setPlainSummary(String plainSummary) {
        this.plainSummary = plainSummary;
    }

    public List<RiskPoint> getRiskPoints() {
        return riskPoints;
    }

    public void setRiskPoints(List<RiskPoint> riskPoints) {
        this.riskPoints = riskPoints;
    }

    public List<MissingClause> getMissingClauses() {
        return missingClauses;
    }

    public void setMissingClauses(List<MissingClause> missingClauses) {
        this.missingClauses = missingClauses;
    }
}
