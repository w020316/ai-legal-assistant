package com.lawai.legalassistant.modules.template.dto;

/**
 * 文书生成结果
 */
public class GenerateResult {

    /** 生成的文书内容 */
    private String content;

    public GenerateResult() {
    }

    public GenerateResult(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
