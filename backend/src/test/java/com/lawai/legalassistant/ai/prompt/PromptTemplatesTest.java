package com.lawai.legalassistant.ai.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PromptTemplates} 单元测试（v1.11.0 新增）
 * <p>
 * 重点关注 v1.11.0 修复 C-3 的 prompt 注入防御：
 * 变量值中的 { } 字符会被转义，防止用户输入构成新的占位符。
 */
@DisplayName("PromptTemplates 提示词模板")
class PromptTemplatesTest {

    @Nested
    @DisplayName("render：模板渲染")
    class RenderMethod {

        @Test
        @DisplayName("单变量替换")
        void singleVariable() {
            String template = "你好，{name}！";
            Map<String, String> vars = Map.of("name", "张三");
            assertThat(PromptTemplates.render(template, vars)).isEqualTo("你好，张三！");
        }

        @Test
        @DisplayName("多变量替换")
        void multipleVariables() {
            String template = "{greeting}，{name}！今天是{day}。";
            Map<String, String> vars = new HashMap<>();
            vars.put("greeting", "你好");
            vars.put("name", "李四");
            vars.put("day", "周一");
            assertThat(PromptTemplates.render(template, vars))
                    .isEqualTo("你好，李四！今天是周一。");
        }

        @Test
        @DisplayName("变量值为 null 时替换为空字符串")
        void nullValue() {
            String template = "name=[{name}]";
            Map<String, String> vars = new HashMap<>();
            vars.put("name", null);
            assertThat(PromptTemplates.render(template, vars)).isEqualTo("name=[]");
        }

        @Test
        @DisplayName("模板中无占位符原样返回")
        void noPlaceholder() {
            String template = "这是一段普通文本";
            Map<String, String> vars = Map.of("unused", "value");
            assertThat(PromptTemplates.render(template, vars)).isEqualTo("这是一段普通文本");
        }

        @Test
        @DisplayName("空 vars 不修改模板")
        void emptyVars() {
            String template = "模板{keep}";
            assertThat(PromptTemplates.render(template, Map.of())).isEqualTo("模板{keep}");
        }

        @Test
        @DisplayName("LEGAL_QA_USER_TEMPLATE 三变量渲染")
        void renderLegalQaTemplate() {
            Map<String, String> vars = new HashMap<>();
            vars.put("context", "相关法条");
            vars.put("history", "历史对话");
            vars.put("question", "用户问题");
            String result = PromptTemplates.render(PromptTemplates.LEGAL_QA_USER_TEMPLATE, vars);
            assertThat(result).contains("相关法条").contains("历史对话").contains("用户问题");
            assertThat(result).doesNotContain("{context}").doesNotContain("{history}").doesNotContain("{question}");
        }
    }

    @Nested
    @DisplayName("安全：prompt 注入防御（v1.11.0 C-3）")
    class PromptInjectionDefense {

        @Test
        @DisplayName("变量值中的 { 字符被转义为 \\{")
        void escapeOpenBrace() {
            String template = "问题：{question}";
            Map<String, String> vars = Map.of("question", "输入{inject}");
            String result = PromptTemplates.render(template, vars);
            // {question} 被替换为 "输入{inject}"，但其中的 { } 应被转义
            // 防止后续 render 轮次将 {inject} 当作新占位符
            assertThat(result).contains("输入\\{inject\\}");
            assertThat(result).doesNotContain("输入{inject}");
        }

        @Test
        @DisplayName("变量值中的 } 字符被转义为 \\}")
        void escapeCloseBrace() {
            String template = "问题：{question}";
            Map<String, String> vars = Map.of("question", "}");
            String result = PromptTemplates.render(template, vars);
            assertThat(result).isEqualTo("问题：\\}");
        }

        @Test
        @DisplayName("变量值包含完整占位符形式不会触发二次替换")
        void noDoubleReplacement() {
            // 假设用户输入 "{question}"，期望该字符串被原样输出（转义后），
            // 而不是被当作新的 {question} 占位符导致无限递归或字符串膨胀
            String template = "{question}";
            Map<String, String> vars = Map.of("question", "{question}");
            String result = PromptTemplates.render(template, vars);
            assertThat(result).isEqualTo("\\{question\\}");
        }

        @Test
        @DisplayName("多变量场景下注入不会跨变量污染")
        void noCrossVariablePollution() {
            String template = "{a} and {b}";
            Map<String, String> vars = new HashMap<>();
            vars.put("a", "{b}");
            vars.put("b", "realB");
            String result = PromptTemplates.render(template, vars);
            // {a} → "\\{b\\}"（转义后不会被 {b} 替换）
            // {b} → "realB"
            assertThat(result).isEqualTo("\\{b\\} and realB");
        }
    }

    @Nested
    @DisplayName("常量：模板内容完整性")
    class TemplateConstants {

        @Test
        @DisplayName("LEGAL_QA_SYSTEM 非空且包含法律三段论")
        void legalQaSystemNotNull() {
            assertThat(PromptTemplates.LEGAL_QA_SYSTEM).isNotBlank();
            assertThat(PromptTemplates.LEGAL_QA_SYSTEM).contains("大前提");
            assertThat(PromptTemplates.LEGAL_QA_SYSTEM).contains("小前提");
            assertThat(PromptTemplates.LEGAL_QA_SYSTEM).contains("结论");
        }

        @Test
        @DisplayName("LEGAL_QA_USER_TEMPLATE 含三占位符")
        void legalQaUserTemplateHasPlaceholders() {
            String t = PromptTemplates.LEGAL_QA_USER_TEMPLATE;
            assertThat(t).contains("{context}").contains("{history}").contains("{question}");
        }

        @Test
        @DisplayName("CONTRACT_REVIEW_SYSTEM 非空且要求 JSON 输出")
        void contractReviewSystemNotNull() {
            assertThat(PromptTemplates.CONTRACT_REVIEW_SYSTEM).isNotBlank();
            assertThat(PromptTemplates.CONTRACT_REVIEW_SYSTEM).contains("JSON");
        }

        @Test
        @DisplayName("IMAGE_OCR_SYSTEM 与 IMAGE_OCR_USER 非空")
        void imageOcrConstants() {
            assertThat(PromptTemplates.IMAGE_OCR_SYSTEM).isNotBlank();
            assertThat(PromptTemplates.IMAGE_OCR_USER).isNotBlank();
        }

        @Test
        @DisplayName("CONTRACT_COMPARE_SYSTEM 非空且含 riskChange")
        void contractCompareSystem() {
            assertThat(PromptTemplates.CONTRACT_COMPARE_SYSTEM).isNotBlank();
            assertThat(PromptTemplates.CONTRACT_COMPARE_SYSTEM).contains("riskChange");
        }

        @Test
        @DisplayName("DOCUMENT_GENERATE_SYSTEM 非空")
        void documentGenerateSystem() {
            assertThat(PromptTemplates.DOCUMENT_GENERATE_SYSTEM).isNotBlank();
        }
    }
}
