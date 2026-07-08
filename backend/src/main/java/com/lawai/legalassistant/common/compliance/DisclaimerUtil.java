package com.lawai.legalassistant.common.compliance;

/**
 * 免责声明工具
 */
public final class DisclaimerUtil {

    private static final String DISCLAIMER = "\n\n---\n*本回答由 AI 生成，仅供参考，不构成法律意见。*";

    /**
     * 获取免责声明文本
     */
    public static String getDisclaimer() {
        return DISCLAIMER;
    }

    /**
     * 将免责声明追加到内容末尾
     */
    public static String append(String content) {
        if (content == null) return DISCLAIMER;
        return content + DISCLAIMER;
    }

    private DisclaimerUtil() {
    }
}
