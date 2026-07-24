package com.lawai.legalassistant.modules.document.dto;

import java.util.List;

/**
 * 双合同比对结果
 * <p>
 * 对比两份合同的差异条款与风险变化，对应 v1.5.0 合同审查深化。
 */
public class ContractCompareVO {

    /** 合同 A 文档 ID */
    private Long docIdA;

    /** 合同 B 文档 ID */
    private Long docIdB;

    /** 整体比对结论 */
    private String summary;

    /** 合同 A 安全评分 */
    private Integer scoreA;

    /** 合同 B 安全评分 */
    private Integer scoreB;

    /** 差异条款列表 */
    private List<ClauseDiff> diffs;

    public Long getDocIdA() {
        return docIdA;
    }

    public void setDocIdA(Long docIdA) {
        this.docIdA = docIdA;
    }

    public Long getDocIdB() {
        return docIdB;
    }

    public void setDocIdB(Long docIdB) {
        this.docIdB = docIdB;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getScoreA() {
        return scoreA;
    }

    public void setScoreA(Integer scoreA) {
        this.scoreA = scoreA;
    }

    public Integer getScoreB() {
        return scoreB;
    }

    public void setScoreB(Integer scoreB) {
        this.scoreB = scoreB;
    }

    public List<ClauseDiff> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<ClauseDiff> diffs) {
        this.diffs = diffs;
    }
}
