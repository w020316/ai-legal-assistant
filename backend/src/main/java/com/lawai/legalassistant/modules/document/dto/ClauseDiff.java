package com.lawai.legalassistant.modules.document.dto;

/**
 * 合同差异条款
 * <p>
 * 双合同比对中单个条款的差异描述，对应 v1.5.0 合同审查深化。
 */
public class ClauseDiff {

    /** 条款名称，如：违约责任条款 */
    private String clause;

    /** 差异类型：新增/删除/修改/缺失 */
    private String type;

    /** 合同 A 中的条款内容（缺失则为空） */
    private String contentA;

    /** 合同 B 中的条款内容（缺失则为空） */
    private String contentB;

    /** 差异说明 */
    private String description;

    /** 风险变化：A→B 是变好还是变差，取值：变好/变差/中性 */
    private String riskChange;

    public String getClause() {
        return clause;
    }

    public void setClause(String clause) {
        this.clause = clause;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContentA() {
        return contentA;
    }

    public void setContentA(String contentA) {
        this.contentA = contentA;
    }

    public String getContentB() {
        return contentB;
    }

    public void setContentB(String contentB) {
        this.contentB = contentB;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRiskChange() {
        return riskChange;
    }

    public void setRiskChange(String riskChange) {
        this.riskChange = riskChange;
    }
}
