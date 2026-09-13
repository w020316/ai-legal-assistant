package com.lawai.legalassistant.modules.casep.service;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.casep.dto.CaseSearchRequest;
import com.lawai.legalassistant.modules.casep.dto.CaseVO;
import com.lawai.legalassistant.modules.casep.mapper.CaseMapper;
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
 * {@link CaseService} 单元测试（v1.18 新增，补覆盖率洼地）
 * <p>
 * 覆盖向量检索、embedding 不可用降级本地文本检索、元数据分页、
 * size/page 钳制（P2#18）与检索服务故障的错误区分。
 */
@DisplayName("CaseService 案例检索")
@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseMapper caseMapper;
    @Mock
    private AiRouter aiRouter;

    private CaseVO caseVo(Long id) {
        CaseVO vo = new CaseVO();
        vo.setId(id);
        vo.setTitle("案例" + id);
        return vo;
    }

    @Test
    @DisplayName("有关键字：向量化检索 + 元数据过滤")
    void searchWithKeywordUsesVector() {
        when(aiRouter.embed("违约金")).thenReturn(new float[]{0.1f, 0.2f});
        when(caseMapper.searchByVectorWithFilters(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(caseVo(1L)));
        CaseSearchRequest req = new CaseSearchRequest();
        req.setKeyword("违约金");

        List<CaseVO> result = new CaseService(caseMapper, aiRouter).search(req);

        assertThat(result).hasSize(1);
        verify(caseMapper, never()).searchByMetadata(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("embedding 不可用：降级本地文本检索")
    void embeddingFailureFallsBackToKeywordText() {
        when(aiRouter.embed(anyString()))
                .thenThrow(BusinessException.of(ResultCode.AI_SERVICE_ERROR, "embedding 不可用"));
        when(caseMapper.searchByKeywordText(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(caseVo(2L)));
        CaseSearchRequest req = new CaseSearchRequest();
        req.setKeyword("判决%书");

        List<CaseVO> result = new CaseService(caseMapper, aiRouter).search(req);

        assertThat(result).hasSize(1);
        verify(caseMapper).searchByKeywordText(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("非 BusinessException 的向量检索故障：不降级，抛检索服务暂不可用")
    void vectorFailureWithoutBusinessExceptionThrows() {
        when(aiRouter.embed(anyString())).thenThrow(new RuntimeException("network down"));
        CaseSearchRequest req = new CaseSearchRequest();
        req.setKeyword("合同");

        assertThatThrownBy(() -> new CaseService(caseMapper, aiRouter).search(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检索服务暂时不可用");
    }

    @Test
    @DisplayName("无关键字：元数据分页检索且 size/page 被钳制（P2#18）")
    void metadataSearchClampsSizeAndPage() {
        when(caseMapper.searchByMetadata(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(caseVo(3L)));
        CaseSearchRequest req = new CaseSearchRequest();
        req.setSize(1000000);
        req.setPage(99999);

        List<CaseVO> result = new CaseService(caseMapper, aiRouter).search(req);

        assertThat(result).hasSize(1);
        // v1.18（P2#18）：size ≤ 100、page ≤ 1000，防止拉全库与深分页慢查询
        verify(caseMapper).searchByMetadata(any(), any(), any(), eq(999 * 100), eq(100));
    }
}
