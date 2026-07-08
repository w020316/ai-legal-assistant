package com.lawai.legalassistant.modules.casep.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.casep.dto.CaseSearchRequest;
import com.lawai.legalassistant.modules.casep.dto.CaseVO;
import com.lawai.legalassistant.modules.casep.service.CaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 案例检索 Controller
 * <p>
 * 提供案例多维检索接口，对应设计方案 5.4 节。
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    /**
     * 案例检索
     * <p>
     * 支持关键词向量检索 + 案由/法院/年份多维筛选，无关键词时按元数据分页查询。
     *
     * @param keyword    关键词
     * @param cause      案由
     * @param courtLevel 法院层级
     * @param year       审理年份
     * @param page       页码
     * @param size       每页条数
     * @return 案例列表
     */
    @GetMapping
    public Result<List<CaseVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cause,
            @RequestParam(required = false) String courtLevel,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        requireLogin();
        CaseSearchRequest req = new CaseSearchRequest();
        req.setKeyword(keyword);
        req.setCause(cause);
        req.setCourtLevel(courtLevel);
        req.setYear(year);
        req.setPage(page);
        req.setSize(size);
        return Result.success(caseService.search(req));
    }

    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
