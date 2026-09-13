package com.lawai.legalassistant.modules.export.controller;

import com.lawai.legalassistant.modules.export.controller.ExportController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link ExportController#sanitizeFilename} 单元测试（v1.18 新增）
 * <p>
 * 锁定 V1-12 修复：截断长度取清洗后字符串长度——连续空白折叠变短后
 * 按原长度 substring 会越界导致导出 500。
 */
@DisplayName("ExportController 导出文件名清洗")
class ExportControllerTest {

    @Test
    @DisplayName("连续空白折叠后不再越界（v1.18 修复 V1-12）")
    void collapsedWhitespaceDoesNotThrow() {
        assertThatCode(() -> ExportController.sanitizeFilename("合同  纠纷\t分析")).doesNotThrowAnyException();
        assertThat(ExportController.sanitizeFilename("合同  纠纷\t分析")).isEqualTo("合同_纠纷_分析");
    }

    @Test
    @DisplayName("非法字符替换与 64 字符截断")
    void illegalCharsAndTruncation() {
        assertThat(ExportController.sanitizeFilename("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j");
        assertThat(ExportController.sanitizeFilename("长".repeat(100))).hasSize(64);
    }

    @Test
    @DisplayName("空/空白标题回退默认文件名")
    void blankFallsBack() {
        assertThat(ExportController.sanitizeFilename(null)).isEqualTo("linzAI导出");
        assertThat(ExportController.sanitizeFilename("   ")).isEqualTo("linzAI导出");
    }
}
