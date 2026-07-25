package com.lawai.legalassistant.common.util;

import com.lawai.legalassistant.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileTypeValidator} 单元测试（v1.11.0 新增）
 * <p>
 * 安全敏感类：通过 magic bytes 防止扩展名伪造。
 * 覆盖 PDF/DOCX/TXT/JPG/PNG 五种类型与异常分支。
 */
@DisplayName("FileTypeValidator 文件类型校验")
class FileTypeValidatorTest {

    /** 构造 PDF magic bytes：%PDF */
    private static byte[] pdfBytes() {
        return new byte[]{0x25, 0x50, 0x44, 0x46, '-', '1', '.', '4'};
    }

    /** 构造 DOCX（ZIP）magic bytes：PK\x03\x04 */
    private static byte[] docxBytes() {
        return new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    }

    /** 构造 JPG magic bytes：\xFF\xD8\xFF */
    private static byte[] jpgBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    }

    /** 构造 PNG magic bytes：\x89PNG\r\n\x1A\n */
    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    @Nested
    @DisplayName("正常场景：扩展名与 magic bytes 匹配")
    class NormalCases {

        @Test
        @DisplayName("PDF 文件校验通过")
        void validatePdf() {
            String type = FileTypeValidator.validate("contract.pdf", pdfBytes());
            assertThat(type).isEqualTo("PDF");
        }

        @Test
        @DisplayName("DOCX 文件校验通过")
        void validateDocx() {
            String type = FileTypeValidator.validate("contract.docx", docxBytes());
            assertThat(type).isEqualTo("DOCX");
        }

        @Test
        @DisplayName("JPG 文件校验通过（.jpg 扩展名）")
        void validateJpg() {
            String type = FileTypeValidator.validate("evidence.jpg", jpgBytes());
            assertThat(type).isEqualTo("JPG");
        }

        @Test
        @DisplayName("JPEG 文件校验通过（.jpeg 扩展名）")
        void validateJpeg() {
            String type = FileTypeValidator.validate("evidence.jpeg", jpgBytes());
            assertThat(type).isEqualTo("JPG");
        }

        @Test
        @DisplayName("PNG 文件校验通过")
        void validatePng() {
            String type = FileTypeValidator.validate("screenshot.png", pngBytes());
            assertThat(type).isEqualTo("PNG");
        }

        @Test
        @DisplayName("TXT 文件跳过 magic bytes 校验")
        void validateTxt() {
            // TXT 无固定 magic bytes，仅校验扩展名，任意内容均应通过
            String type = FileTypeValidator.validate("notes.txt", "hello world".getBytes());
            assertThat(type).isEqualTo("TXT");
        }

        @Test
        @DisplayName("大写扩展名也能识别")
        void validateUppercaseExtension() {
            String type = FileTypeValidator.validate("CONTRACT.PDF", pdfBytes());
            assertThat(type).isEqualTo("PDF");
        }

        @Test
        @DisplayName("混合大小写扩展名也能识别")
        void validateMixedCaseExtension() {
            String type = FileTypeValidator.validate("Contract.PdF", pdfBytes());
            assertThat(type).isEqualTo("PDF");
        }
    }

    @Nested
    @DisplayName("异常场景：参数错误")
    class ParamErrors {

        @Test
        @DisplayName("文件名为空抛 PARAM_ERROR")
        void nullFilename() {
            assertThatThrownBy(() -> FileTypeValidator.validate(null, pdfBytes()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件名不能为空");
        }

        @Test
        @DisplayName("文件内容为 null 抛 PARAM_ERROR")
        void nullBytes() {
            assertThatThrownBy(() -> FileTypeValidator.validate("a.pdf", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容为空");
        }

        @Test
        @DisplayName("文件内容为空数组抛 PARAM_ERROR")
        void emptyBytes() {
            assertThatThrownBy(() -> FileTypeValidator.validate("a.pdf", new byte[0]))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容为空");
        }

        @Test
        @DisplayName("不支持的扩展名抛 PARAM_ERROR")
        void unsupportedExtension() {
            assertThatThrownBy(() -> FileTypeValidator.validate("malware.exe", new byte[]{1, 2, 3}))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支持的文件类型");
        }
    }

    @Nested
    @DisplayName("安全场景：magic bytes 伪造检测")
    class MagicBytesForgery {

        @Test
        @DisplayName("PDF 扩展名但内容非 PDF 抛异常")
        void pdfExtensionWithFakeContent() {
            assertThatThrownBy(() -> FileTypeValidator.validate("fake.pdf", new byte[]{1, 2, 3, 4, 5}))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容与扩展名不匹配");
        }

        @Test
        @DisplayName("DOCX 扩展名但内容为 PDF 抛异常")
        void docxExtensionWithPdfContent() {
            assertThatThrownBy(() -> FileTypeValidator.validate("fake.docx", pdfBytes()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容与扩展名不匹配");
        }

        @Test
        @DisplayName("JPG 扩展名但内容为 PNG 抛异常")
        void jpgExtensionWithPngContent() {
            assertThatThrownBy(() -> FileTypeValidator.validate("fake.jpg", pngBytes()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容与扩展名不匹配");
        }

        @Test
        @DisplayName("PNG 扩展名但内容为 JPG 抛异常")
        void pngExtensionWithJpgContent() {
            assertThatThrownBy(() -> FileTypeValidator.validate("fake.png", jpgBytes()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("文件内容与扩展名不匹配");
        }

        @Test
        @DisplayName("字节数短于 magic bytes 长度时拒绝")
        void bytesShorterThanMagic() {
            // PDF magic 4 字节，仅传 2 字节应拒绝
            assertThatThrownBy(() -> FileTypeValidator.validate("short.pdf", new byte[]{0x25, 0x50}))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
