package com.lawai.legalassistant.modules.document.dto;

/**
 * 缺失条款
 * <p>
 * 合同中应当包含但实际未约定的条款，对应合同审查报告的"缺失条款提醒"板块。
 */
public class MissingClause {

    /** 条款名称，如：违约责任条款 */
    private String name;

    /** 重要性：高/中/低 */
    private String importance;

    /** 缺失风险说明 */
    private String risk;

    /** 补充建议（建议补充的条款文本） */
    private String suggestion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
