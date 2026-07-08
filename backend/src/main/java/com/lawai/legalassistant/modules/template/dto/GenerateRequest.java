package com.lawai.legalassistant.modules.template.dto;

import java.util.Map;

/**
 * 文书生成请求
 */
public class GenerateRequest {

    /** 模板 ID */
    private Long templateId;

    /** 要素键值对 */
    private Map<String, String> elements;

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Map<String, String> getElements() {
        return elements;
    }

    public void setElements(Map<String, String> elements) {
        this.elements = elements;
    }
}
