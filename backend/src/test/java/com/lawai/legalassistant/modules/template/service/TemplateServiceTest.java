package com.lawai.legalassistant.modules.template.service;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.template.dto.GenerateRequest;
import com.lawai.legalassistant.modules.template.dto.GenerateResult;
import com.lawai.legalassistant.modules.template.dto.TemplateVO;
import com.lawai.legalassistant.modules.template.mapper.TemplateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link TemplateService} 单元测试（v1.18 新增，补覆盖率洼地）
 * <p>
 * 覆盖模板列表/详情/404、文书生成的成功与失败分支、要素缺失等边界。
 */
@DisplayName("TemplateService 模板与文书生成")
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateMapper templateMapper;
    @Mock
    private AiRouter aiRouter;

    private TemplateVO template(Long id, String rawText) {
        TemplateVO vo = new TemplateVO();
        vo.setId(id);
        vo.setTitle("劳动合同模板");
        vo.setRawText(rawText);
        return vo;
    }

    @Test
    @DisplayName("列表与详情")
    void listAndGetTemplate() {
        when(templateMapper.selectTemplates(null)).thenReturn(List.of(template(1L, "正文")));
        when(templateMapper.selectTemplateById(1L)).thenReturn(template(1L, "正文"));

        TemplateService svc = new TemplateService(templateMapper, aiRouter);
        assertThat(svc.list(null)).hasSize(1);
        assertThat(svc.getTemplate(1L).getTitle()).isEqualTo("劳动合同模板");
    }

    @Test
    @DisplayName("模板不存在抛 404")
    void templateNotFound() {
        when(templateMapper.selectTemplateById(99L)).thenReturn(null);
        assertThatThrownBy(() -> new TemplateService(templateMapper, aiRouter).getTemplate(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("生成成功：模板 + 要素进入 prompt")
    void generateSuccess() {
        when(templateMapper.selectTemplateById(1L)).thenReturn(template(1L, "第一条 甲方：{甲方}"));
        when(aiRouter.chat(anyString(), anyString())).thenReturn("生成的完整文书内容");
        GenerateRequest req = new GenerateRequest();
        req.setTemplateId(1L);
        req.setElements(Map.of("甲方", "某某公司"));

        GenerateResult result = new TemplateService(templateMapper, aiRouter).generate(42L, req);

        assertThat(result.getContent()).isEqualTo("生成的完整文书内容");
    }

    @Test
    @DisplayName("生成拒绝：模板 ID 缺失 / 模板内容为空")
    void generateValidation() {
        TemplateService svc = new TemplateService(templateMapper, aiRouter);
        GenerateRequest noId = new GenerateRequest();
        assertThatThrownBy(() -> svc.generate(1L, noId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板 ID 不能为空");

        when(templateMapper.selectTemplateById(2L)).thenReturn(template(2L, "  "));
        GenerateRequest emptyTemplate = new GenerateRequest();
        emptyTemplate.setTemplateId(2L);
        assertThatThrownBy(() -> svc.generate(1L, emptyTemplate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容为空");
    }

    @Test
    @DisplayName("AI 故障转业务异常")
    void generateAiFailureWrapped() {
        when(templateMapper.selectTemplateById(1L)).thenReturn(template(1L, "正文"));
        when(aiRouter.chat(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        GenerateRequest req = new GenerateRequest();
        req.setTemplateId(1L);

        assertThatThrownBy(() -> new TemplateService(templateMapper, aiRouter).generate(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文书生成失败");
    }
}
