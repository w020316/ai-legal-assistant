package com.lawai.legalassistant.common.compliance;

import java.util.HashSet;
import java.util.Set;

/**
 * 敏感词过滤器（基础实现）
 * <p>
 * 内置一份基础敏感词列表，支持动态添加。filter 方法将命中的敏感词替换为 ***。
 * 生产环境建议接入专业敏感词服务或 DFA 算法。
 */
public class SensitiveWordFilter {

    private static final Set<String> DEFAULT_WORDS = new HashSet<>();

    static {
        // 基础敏感词，实际项目应从配置或外部加载
        DEFAULT_WORDS.add("反动");
        DEFAULT_WORDS.add("暴恐");
        DEFAULT_WORDS.add("色情");
        DEFAULT_WORDS.add("赌博");
        DEFAULT_WORDS.add("毒品");
        DEFAULT_WORDS.add("诈骗");
    }

    private final Set<String> words = new HashSet<>(DEFAULT_WORDS);

    /**
     * 添加敏感词
     */
    public void addWord(String word) {
        if (word != null && !word.isBlank()) {
            words.add(word.trim());
        }
    }

    /**
     * 过滤文本，将敏感词替换为 ***
     *
     * @param text 原始文本
     * @return 过滤后文本
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (String w : words) {
            if (result.contains(w)) {
                result = result.replace(w, "***");
            }
        }
        return result;
    }

    /**
     * 是否包含敏感词
     */
    public boolean contains(String text) {
        if (text == null) return false;
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
