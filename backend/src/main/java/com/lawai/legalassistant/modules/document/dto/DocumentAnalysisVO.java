package com.lawai.legalassistant.modules.document.dto;

import java.util.List;

/**
 * 文档分析结果
 */
public class DocumentAnalysisVO {

    /** 整体摘要 */
    private String summary;

    /** 风险点列表 */
    private List<RiskPoint> riskPoints;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<RiskPoint> getRiskPoints() {
        return riskPoints;
    }

    public void setRiskPoints(List<RiskPoint> riskPoints) {
        this.riskPoints = riskPoints;
    }
}
