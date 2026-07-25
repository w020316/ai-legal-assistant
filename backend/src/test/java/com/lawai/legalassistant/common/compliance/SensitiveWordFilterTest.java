package com.lawai.legalassistant.common.compliance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SensitiveWordFilter} 单元测试（v1.11.0 新增）
 * <p>
 * 覆盖默认敏感词过滤、动态添加、contains 判断与边界条件。
 */
@DisplayName("SensitiveWordFilter 敏感词过滤")
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
    }

    @Nested
    @DisplayName("filter：替换敏感词为 ***")
    class FilterMethod {

        @Test
        @DisplayName("命中默认敏感词'赌博'被替换")
        void filterDefaultWord() {
            String result = filter.filter("这是一个赌博网站");
            assertThat(result).isEqualTo("这是一个***网站");
        }

        @Test
        @DisplayName("命中多个敏感词全部替换")
        void filterMultipleWords() {
            String result = filter.filter("反动和色情内容");
            assertThat(result).isEqualTo("***和***内容");
        }

        @Test
        @DisplayName("同一敏感词多次出现全部替换")
        void filterRepeatedWord() {
            String result = filter.filter("赌博赌博赌博");
            assertThat(result).isEqualTo("*********");
        }

        @Test
        @DisplayName("不命中敏感词原样返回")
        void filterCleanText() {
            String result = filter.filter("这是一段正常的法律咨询内容");
            assertThat(result).isEqualTo("这是一段正常的法律咨询内容");
        }

        @Test
        @DisplayName("null 输入返回 null")
        void filterNull() {
            assertThat(filter.filter(null)).isNull();
        }

        @Test
        @DisplayName("空字符串原样返回")
        void filterEmpty() {
            assertThat(filter.filter("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("contains：判断是否包含敏感词")
    class ContainsMethod {

        @Test
        @DisplayName("包含默认敏感词返回 true")
        void containsDefaultWord() {
            assertThat(filter.contains("涉及毒品交易")).isTrue();
        }

        @Test
        @DisplayName("不包含敏感词返回 false")
        void containsCleanText() {
            assertThat(filter.contains("正常的法律问题")).isFalse();
        }

        @Test
        @DisplayName("null 输入返回 false")
        void containsNull() {
            assertThat(filter.contains(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串返回 false")
        void containsEmpty() {
            assertThat(filter.contains("")).isFalse();
        }
    }

    @Nested
    @DisplayName("addWord：动态添加敏感词")
    class AddWordMethod {

        @Test
        @DisplayName("添加新敏感词后能被过滤")
        void addAndFilter() {
            filter.addWord("测试敏感词");
            assertThat(filter.filter("这是测试敏感词内容")).isEqualTo("这是***内容");
            assertThat(filter.contains("这是测试敏感词内容")).isTrue();
        }

        @Test
        @DisplayName("添加 null 不影响原有过滤")
        void addNull() {
            filter.addWord(null);
            // 默认敏感词仍应正常工作
            assertThat(filter.contains("赌博")).isTrue();
        }

        @Test
        @DisplayName("添加空字符串不影响原有过滤")
        void addBlank() {
            filter.addWord("   ");
            assertThat(filter.contains("赌博")).isTrue();
        }

        @Test
        @DisplayName("添加带空白的词会 trim")
        void addWithWhitespace() {
            filter.addWord("  自定义词  ");
            // trim 后应为 "自定义词"
            assertThat(filter.contains("这是自定义词内容")).isTrue();
        }
    }
}
