package com.lawai.legalassistant.modules.casep.dto;

import jakarta.validation.constraints.Max;

/**
 * 案例检索请求
 */
public class CaseSearchRequest {

    /** 关键词（自然语言，用于向量检索） */
    private String keyword;

    /** 案由 */
    private String cause;

    /** 法院层级 */
    private String courtLevel;

    /** 审理年份 */
    private Integer year;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    @Max(value = 100, message = "单页最多 100 条")
    private Integer size = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public String getCourtLevel() {
        return courtLevel;
    }

    public void setCourtLevel(String courtLevel) {
        this.courtLevel = courtLevel;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
