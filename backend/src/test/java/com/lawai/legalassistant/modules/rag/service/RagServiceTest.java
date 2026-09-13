package com.lawai.legalassistant.modules.rag.service;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertThat(ragService.retrieveDeep("", 5, 1L)).isEmpty();
        assertThat(ragService.retrieveDeep(null, 5, 1L)).isEmpty();
    }

    @Test
    @DisplayName("embedding 不可用时退化为关键字检索（不抛异常）")
    void fallbackToKeywordWhenEmbeddingFails() {
        // embed 抛异常 → 触发关键字降级
        when(aiRouter.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
        when(chunkMapper.searchByKeywordText(anyString(), anyInt(), any())).thenReturn(
                List.of(chunk(1L, "包含不可抗力条款"), chunk(2L, "包含违约金条款")));

        List<RetrievedChunk> result = ragService.retrieveDeep("不可抗力违约金比例", 5, 1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("关键字检索对通配符进行转义，防止扩大匹配")
    void escapesLikeWildcards() {
        when(aiRouter.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
        when(chunkMapper.searchByKeywordText(anyString(), anyInt(), any())).thenReturn(
                List.of(chunk(3L, "包含 100% 违约金条款")));

        ragService.retrieveDeep("违约金100%_条款", 5, 1L);

        // 验证传给 Mapper 的关键字不含裸通配符（已转义），且 ownerId 透传到 SQL（PRIVATE 按归属放行）
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(chunkMapper).searchByKeywordText(captor.capture(), anyInt(), eq(1L));
        String escaped = captor.getValue();
        // 通配符已转义（% → \%，_ → \_），避免 ILIKE 通配扩大匹配
        assertThat(escaped).contains("\\%").contains("\\_");
    }

    @Test
    @DisplayName("语义与关键字结果按切片 ID 去重合并")
    void mergesSemanticAndKeywordDedup() {
        // 语义检索可用：返回 id 1、2
        when(aiRouter.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(chunkMapper.searchByVector(anyString(), anyInt(), any()))
                .thenReturn(List.of(chunk(1L, "语义命中"), chunk(2L, "语义命中2")));
        // 关键字检索进一步命中 id 2（重复）、3
        when(chunkMapper.searchByKeywordText(anyString(), anyInt(), any()))
                .thenReturn(List.of(chunk(2L, "关键字命中"), chunk(3L, "关键字命中2")));

        List<RetrievedChunk> result = ragService.retrieveDeep("不可抗力违约金", 5, 1L);

        assertThat(result).hasSize(3);
        // 顺序：先语义（1,2），再去重追加关键字（3），id 2 不重复
        assertThat(result).extracting(RetrievedChunk::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("v1.18：大文档切片截断到上限，embedBatch 按 64 分批且批量插入")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ingestTruncatesAndShardsEmbedding() {
        doAnswer(inv -> {
            KnowledgeDoc d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        }).when(docMapper).insert(any(KnowledgeDoc.class));
        when(aiRouter.embedBatch(anyList())).thenAnswer(inv -> {
            List<String> in = inv.getArgument(0);
            List<float[]> out = new java.util.ArrayList<>();
            for (int i = 0; i < in.size(); i++) {
                out.add(new float[]{0.1f, 0.2f});
            }
            return out;
        });

        // 单段 ~260,000 字符 → 硬切约 620 片 > 上限 500 → 截断
        String big = "测".repeat(260000);
        ragService.ingestDocument("大文档", "LAW", "test", big, "PUBLIC", null);

        org.mockito.ArgumentCaptor<List> batchCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(aiRouter, atLeast(2)).embedBatch(batchCaptor.capture());
        // 每批不超过 EMBED_BATCH_SIZE(64)，且总数恰为截断上限 500
        batchCaptor.getAllValues().forEach(b -> assertThat(b.size()).isLessThanOrEqualTo(64));
        int total = batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(500);
        // 切片落库走批量 insertChunks，且不再逐条 insert
        verify(chunkMapper, atLeastOnce()).insertChunks(anyList());
        verify(chunkMapper, never()).insertChunk(any(KnowledgeChunk.class));
    }

    @Test
    @DisplayName("v1.18：embedding 返回条数与切片数不一致时拒绝入库（防错位）")
    void ingestRejectsEmbeddingCountMismatch() {
        doAnswer(inv -> {
            KnowledgeDoc d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        }).when(docMapper).insert(any(KnowledgeDoc.class));
        // 700 字符 → 2 片；stub 只返回 1 个向量 → 条数不一致 → 拒绝
        when(aiRouter.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));

        assertThatThrownBy(() -> ragService.ingestDocument("t", "LAW", "s", "测".repeat(700), "PUBLIC", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
        verify(chunkMapper, never()).insertChunks(anyList());
    }
}