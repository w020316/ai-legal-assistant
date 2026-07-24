package com.lawai.legalassistant.common.util;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;

import java.util.Locale;

/**
 * 文件类型校验工具
 * <p>
 * 通过读取文件头 magic bytes（魔数）校验真实文件类型，
 * 防止攻击者篡改扩展名上传恶意文件（如 .exe 改名为 .pdf）。
 * v1.7.0 新增。
 */
public final class FileTypeValidator {

    private FileTypeValidator() {
    }

    /** PDF 文件头：%PDF */
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

    /** DOCX 本质是 ZIP 压缩包，文件头：PK\x03\x04 */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** JPG 文件头：\xFF\xD8\xFF */
    private static final byte[] JPG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** PNG 文件头：\x89PNG\r\n\x1A\n */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * 校验文件类型：同时校验扩展名与 magic bytes
     * <p>
     * TXT 文件无固定 magic bytes，仅校验扩展名。
     *
     * @param filename 原始文件名（用于扩展名校验）
     * @param bytes    文件字节数组（用于 magic bytes 校验）
     * @return 标准化的文件类型字符串（PDF/DOCX/TXT/JPG/PNG）
     * @throws BusinessException 扩展名不支持或 magic bytes 不匹配
     */
    public static String validate(String filename, byte[] bytes) {
        if (filename == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件名不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件内容为空");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        String extType = detectExtension(lower);

        // TXT 无固定 magic bytes，跳过内容校验
        if ("TXT".equals(extType)) {
            return extType;
        }

        // 校验 magic bytes 与扩展名是否匹配
        if (!matchesMagic(extType, bytes)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR,
                    "文件内容与扩展名不匹配，疑似伪造文件类型");
        }
        return extType;
    }

    /**
     * 根据扩展名判断文件类型
     */
    private static String detectExtension(String lowerFilename) {
        if (lowerFilename.endsWith(".pdf")) {
            return "PDF";
        }
        if (lowerFilename.endsWith(".docx")) {
            return "DOCX";
        }
        if (lowerFilename.endsWith(".txt")) {
            return "TXT";
        }
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "JPG";
        }
        if (lowerFilename.endsWith(".png")) {
            return "PNG";
        }
        throw BusinessException.of(ResultCode.PARAM_ERROR,
                "不支持的文件类型，仅支持 PDF/DOCX/TXT/JPG/PNG");
    }

    /**
     * 校验文件头 magic bytes 是否与声明类型匹配
     */
    private static boolean matchesMagic(String type, byte[] bytes) {
        return switch (type) {
            case "PDF" -> startsWith(bytes, PDF_MAGIC);
            case "DOCX" -> startsWith(bytes, ZIP_MAGIC);
            case "JPG" -> startsWith(bytes, JPG_MAGIC);
            case "PNG" -> startsWith(bytes, PNG_MAGIC);
            default -> true;
        };
    }

    /**
     * 判断字节数组是否以指定 magic bytes 开头
     */
    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
