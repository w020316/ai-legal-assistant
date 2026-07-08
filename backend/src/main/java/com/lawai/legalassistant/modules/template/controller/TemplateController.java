package com.lawai.legalassistant.modules.template.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.template.dto.GenerateRequest;
import com.lawai.legalassistant.modules.template.dto.GenerateResult;
import com.lawai.legalassistant.modules.template.dto.TemplateVO;
import com.lawai.legalassistant.modules.template.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板 Controller
 * <p>
 * 提供模板列表、详情、文书生成接口，对应设计方案 5.3 节。
 */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * 模板列表（按分类筛选）
     *
     * @param category 分类
     * @return 模板列表
     */
    @GetMapping
    public Result<List<TemplateVO>> list(@RequestParam(required = false) String category) {
        requireLogin();
        return Result.success(templateService.list(category));
    }

    /**
     * 模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    public Result<TemplateVO> detail(@PathVariable Long id) {
        requireLogin();
        return Result.success(templateService.getTemplate(id));
    }

    /**
     * 基于模板生成文书
     *
     * @param id  模板 ID
     * @param req 生成请求（要素键值对）
     * @return 生成结果
     */
    @PostMapping("/{id}/generate")
    public Result<GenerateResult> generate(@PathVariable Long id, @RequestBody(required = false) GenerateRequest req) {
        Long userId = requireLogin();
        if (req == null) {
            req = new GenerateRequest();
        }
        req.setTemplateId(id);
        return Result.success(templateService.generate(userId, req));
    }

    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
