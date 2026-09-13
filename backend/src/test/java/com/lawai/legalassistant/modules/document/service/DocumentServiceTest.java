package com.lawai.legalassistant.modules.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.document.dto.DocumentAnalysisVO;
import com.lawai.legalassistant.modules.document.dto.UserDocumentVO;
import com.lawai.legalassistant.modules.document.entity.UserDocument;
import com.lawai.legalassistant.modules.document.mapper.UserDocumentMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentService} 单元测试（v1.18 新增，补覆盖率洼地）
 * <p>
 * 覆盖分析缓存命中/未命中、AI 失败包装、列表 VO 派生 analysisStatus（V1-02）
 * 与 getAnalysis 空值边界。upload 涉及真实文件系统，归入集成验证。
 */
@DisplayName("DocumentService 文档服务")
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private UserDocumentMapper documentMapper;
    @Mock
    private AiRouter aiRouter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        // v1.18：LambdaQueryWrapper.select(Class, Predicate) 依赖 MP TableInfo 元数据，
        // 纯单测上下文无 Mapper 扫描，需手动初始化
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                UserDocument.class);
    }

    private UserDocument doc(Long id, Long userId, String ocrText, String analysisResult) {
        UserDocument d = new UserDocument();
        d.setId(id);
        d.setUserId(userId);
        d.setFilename("合同.pdf");
        d.setFileType("PDF");
        d.setFileSize(1024L);
        d.setVersion(1L);
        d.setOcrText(ocrText);
        d.setAnalysisResult(analysisResult);
        return d;
    }

    @Test
    @DisplayName("分析：无缓存时调 AI 并写库")
    void analyzeCallsAiAndSaves() {
        when(documentMapper.selectById(5L)).thenReturn(doc(5L, 1L, "合同正文", null));
        when(aiRouter.chat(anyString(), anyString())).thenReturn("{\"summary\":\"ok\",\"riskPoints\":[]}");

        DocumentAnalysisVO vo = new DocumentService(documentMapper, aiRouter, objectMapper).analyze(1L, 5L);

        assertThat(vo.getSummary()).isEqualTo("ok");
        verify(documentMapper).updateAnalysisResult(eq(5L), anyString());
    }

    @Test
    @DisplayName("分析：命中缓存直接返回，不调 AI")
    void analyzeHitsCache() {
        when(documentMapper.selectById(5L))
                .thenReturn(doc(5L, 1L, "合同正文", "{\"summary\":\"cached\"}"));

        DocumentAnalysisVO vo = new DocumentService(documentMapper, aiRouter, objectMapper).analyze(1L, 5L);

        assertThat(vo.getSummary()).isEqualTo("cached");
        verify(aiRouter, never()).chat(anyString(), anyString());
        verify(documentMapper, never()).updateAnalysisResult(any(), anyString());
    }

    @Test
    @DisplayName("分析：文本为空拒绝；AI 故障包装为业务异常")
    void analyzeValidationAndFailure() {
        when(documentMapper.selectById(6L)).thenReturn(doc(6L, 1L, " ", null));
        DocumentService svc = new DocumentService(documentMapper, aiRouter, objectMapper);
        assertThatThrownBy(() -> svc.analyze(1L, 6L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档文本为空");

        when(documentMapper.selectById(7L)).thenReturn(doc(7L, 1L, "正文", null));
        when(aiRouter.chat(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> svc.analyze(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档分析失败");
    }

    @Test
    @DisplayName("列表：返回轻量 VO 并派生 analysisStatus（V1-02）")
    void listDerivesAnalysisStatus() {
        when(documentMapper.selectList(any())).thenReturn(List.of(
                doc(1L, 1L, "正文", "{\"summary\":\"ok\"}"),
                doc(2L, 1L, "正文", null)));

        List<UserDocumentVO> result = new DocumentService(documentMapper, aiRouter, objectMapper).list(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAnalysisStatus()).isEqualTo("completed");
        assertThat(result.get(1).getAnalysisStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("越权/不存在：文档不存在或无权限")
    void analyzeRejectsForeignDoc() {
        when(documentMapper.selectById(8L)).thenReturn(doc(8L, 2L, "正文", null));
        assertThatThrownBy(() -> new DocumentService(documentMapper, aiRouter, objectMapper).analyze(1L, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在或无权限");
    }
}
