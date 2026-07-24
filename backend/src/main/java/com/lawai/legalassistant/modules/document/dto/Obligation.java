package com.lawai.legalassistant.modules.document.dto;

/**
 * 合同义务时间线项
 * <p>
 * 梳理合同中各方需要履行的义务及其截止日期，对应 v1.5.0 合同审查深化。
 */
public class Obligation {

    /** 义务描述，如：甲方应在签约后 30 日内支付首期款 */
    private String obligation;

    /** 截止日期或时间节点，如：签约后 30 日内 / 2026-12-31 前 */
    private String deadline;

    /** 义务方：甲方/乙方/双方 */
    private String party;

    /** 未履行的后果，如：逾期按日万分之五支付违约金 */
    private String consequence;

    public String getObligation() {
        return obligation;
    }

    public void setObligation(String obligation) {
        this.obligation = obligation;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getParty() {
        return party;
    }

    public void setParty(String party) {
        this.party = party;
    }

    public String getConsequence() {
        return consequence;
    }

    public void setConsequence(String consequence) {
        this.consequence = consequence;
    }
}
