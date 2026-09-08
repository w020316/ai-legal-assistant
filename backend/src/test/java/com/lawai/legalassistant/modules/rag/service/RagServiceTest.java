package com.lawai.legalassistant.modules.rag.service;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeChunkMapper;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link RagService} 检索套件单元测试（v1.12.0 新增）
 * <p>
 * 覆盖 retrieveDeep 复合检索：embedding 不可用时退化为关键字检索、
 * 语义 + 关键字合并去重、非法关键字转义等降级与容错路径。
 */
@DisplayName("RagService 检索套件")
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private AiRouter aiRouter;
    @Mock
    private KnowledgeDocMapper docMapper;
    @Mock
    private KnowledgeChunkMapper chunkMapper;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(aiRouter, docMapper, chunkMapper);
    }

    private RetrievedChunk chunk(Long id, String snippet) {
        RetrievedChunk c = new RetrievedChunk();
        c.setId(id);
        c.setDocId(id);
        c.setTitle("标题" + id);
        c.setSource("来源" + id);
        c.setSnippet(snippet);
        return c;
    }

    @Test
    @DisplayName("空问题返回空列表")
    void blankQuestionReturnsEmpty() {
        assertThat(ragService.retrieveDeep("", 5)).isEmpty();
        assertThat(ragService.retrieveDeep(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("embedding 不可用时退化为关键字检索（不抛异常）")
    void fallbackToKeywordWhenEmbeddingFails() {
        // embed 抛异常 → 触发关键字降级
        when(aiRouter.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
        when(chunkMapper.searchByKeywordText(anyString(), anyInt())).thenReturn(
                List.of(chunk(1L, "包含不可抗力条款"), chunk(2L, "包含违约金条款")));

        List<RetrievedChunk> result = ragService.retrieveDeep("不可抗力违约金比例", 5);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("关键字检索对通配符进行转义，防止扩大匹配")
    void escapesLikeWildcards() {
        when(aiRouter.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
        when(chunkMapper.searchByKeywordText(anyString(), anyInt())).thenReturn(
                List.of(chunk(3L, "包含 100% 违约金条款")));

        ragService.retrieveDeep("违约金100%_条款", 5);

        // 验证传给 Mapper 的关键字不含裸通配符（已转义）
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(chunkMapper).searchByKeywordText(captor.capture(), anyInt());
        String escaped = captor.getValue();
        // 通配符已转义（% → \%，_ → \_），避免 ILIKE 通配扩大匹配
        assertThat(escaped).contains("\\%").contains("\\_");
    }

    @Test
    @DisplayName("语义与关键字结果按切片 ID 去重合并")
    void mergesSemanticAndKeywordDedup() {
        // 语义检索可用：返回 id 1、2
        when(aiRouter.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(chunkMapper.searchByVector(anyString(), anyInt()))
                .thenReturn(List.of(chunk(1L, "语义命中"), chunk(2L, "语义命中2")));
        // 关键字检索进一步命中 id 2（重复）、3
        when(chunkMapper.searchByKeywordText(anyString(), anyInt()))
                .thenReturn(List.of(chunk(2L, "关键字命中"), chunk(3L, "关键字命中2")));

        List<RetrievedChunk> result = ragService.retrieveDeep("不可抗力违约金", 5);

        assertThat(result).hasSize(3);
        // 顺序：先语义（1,2），再去重追加关键字（3），id 2 不重复
        assertThat(result).extracting(RetrievedChunk::getId).containsExactly(1L, 2L, 3L);
    }
}