package com.lawai.legalassistant.modules.template.service;

import com.lawai.legalassistant.ai.client.AgnesClient;
import com.lawai.legalassistant.ai.prompt.PromptTemplates;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.template.dto.GenerateRequest;
import com.lawai.legalassistant.modules.template.dto.GenerateResult;
import com.lawai.legalassistant.modules.template.dto.TemplateVO;
import com.lawai.legalassistant.modules.template.mapper.TemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 模板服务
 * <p>
 * 提供模板列表查询、详情获取、基于模板的文书生成能力，
 * 对应设计方案 5.3 节法律文书生成。
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateMapper templateMapper;
    private final AgnesClient agnesClient;

    public TemplateService(TemplateMapper templateMapper, AgnesClient agnesClient) {
        this.templateMapper = templateMapper;
        this.agnesClient = agnesClient;
    }

    /**
     * 模板列表
     *
     * @param category 分类，null 时查全部
     * @return 模板列表
     */
    public List<TemplateVO> list(String category) {
        return templateMapper.selectTemplates(category);
    }

    /**
     * 模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    public TemplateVO getTemplate(Long id) {
        TemplateVO vo = templateMapper.selectTemplateById(id);
        if (vo == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "模板不存在");
        }
        return vo;
    }

    /**
     * 基于模板生成文书
     *
     * @param userId 用户 ID
     * @param req    生成请求（模板 ID + 要素）
     * @return 生成结果
     */
    public GenerateResult generate(Long userId, GenerateRequest req) {
        if (req == null || req.getTemplateId() == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "模板 ID 不能为空");
        }
        TemplateVO template = getTemplate(req.getTemplateId());
        if (template.getRawText() == null || template.getRawText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "模板内容为空");
        }

        try {
            String userPrompt = buildGeneratePrompt(template, req.getElements());
            String content = agnesClient.chat(PromptTemplates.DOCUMENT_GENERATE_SYSTEM, userPrompt);
            log.info("文书生成完成: userId={}, templateId={}", userId, req.getTemplateId());
            return new GenerateResult(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文书生成失败: templateId={}", req.getTemplateId(), e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "文书生成失败，请稍后重试");
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构造文书生成 Prompt
     */
    private String buildGeneratePrompt(TemplateVO template, Map<String, String> elements) {
        StringBuilder sb = new StringBuilder();
        sb.append("【模板标题】\n").append(template.getTitle()).append("\n\n");
        sb.append("【模板内容】\n").append(template.getRawText()).append("\n\n");

        if (elements != null && !elements.isEmpty()) {
            sb.append("【用户填写的要素】\n");
            for (Map.Entry<String, String> e : elements.entrySet()) {
                sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【生成要求】\n");
        sb.append("请根据以上模板和要素，生成一份完整的法律文书。\n");
        sb.append("1. 保留模板的整体结构和条款编号。\n");
        sb.append("2. 将用户填写的要素代入到对应位置。\n");
        sb.append("3. 对模板中未提供的要素，根据法律惯例补充合理默认值并标注。\n");
        sb.append("4. 生成的文书应可直接使用，语言专业规范。\n");
        return sb.toString();
    }
}
