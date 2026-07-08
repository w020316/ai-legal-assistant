package com.lawai.legalassistant.ai.prompt;

import java.util.Map;

/**
 * Prompt 模板管理
 * <p>
 * 存放系统提示词与用户消息模板，对应设计方案 5.1 节智能法律问答。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * 法律问答系统提示词
     */
    public static final String LEGAL_QA_SYSTEM = """
            你是一名专业的中国法律助手，面向律师、法务等法律从业者。

            回答规范：
            1. 优先依据提供的【检索到的法律资料】作答，资料未覆盖时可结合专业知识补充并标注"非来源于检索"。
            2. 答案结构：结论摘要 → 法律依据 → 详细分析 → 实务建议。
            3. 引用法条须精确到条/款/项，引用案例须标注案号/案由/裁判要旨。
            4. 使用专业但清晰的语言，保留法律术语，不做过度普法降级。
            5. 若问题超出法律范畴或资料不足，明确说明，不得编造法条或案号。
            6. 末尾自动追加免责声明："本回答由 AI 生成，仅供参考，不构成法律意见"。
            """;

    /**
     * 用户消息模板，含 {context} {history} {question} 占位符
     */
    public static final String LEGAL_QA_USER_TEMPLATE = """
            【检索到的法律资料】
            {context}

            【对话历史】
            {history}

            【用户问题】
            {question}
            """;

    /**
     * 简单字符串替换渲染
     *
     * @param template 模板，含 {key} 占位符
     * @param vars     变量映射
     * @return 渲染后的字符串
     */
    public static String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            result = result.replace("{" + e.getKey() + "}", val);
        }
        return result;
    }
}
