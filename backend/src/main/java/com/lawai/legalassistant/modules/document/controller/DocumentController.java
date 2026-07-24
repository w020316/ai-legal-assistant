package com.lawai.legalassistant.modules.document.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.document.dto.ContractCompareVO;
import com.lawai.legalassistant.modules.document.dto.DocumentAnalysisVO;
import com.lawai.legalassistant.modules.document.dto.UploadResponse;
import com.lawai.legalassistant.modules.document.entity.UserDocument;
import com.lawai.legalassistant.modules.document.service.DocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Controller
 * <p>
 * 提供文档上传、列表、分析接口，对应设计方案 5.2 节。
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文档（multipart/form-data）
     *
     * @param file 文件
     * @return 上传响应
     */
    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = requireLogin();
        return Result.success(documentService.upload(userId, file));
    }

    /**
     * 用户文档列表
     */
    @GetMapping
    public Result<List<UserDocument>> list() {
        Long userId = requireLogin();
        return Result.success(documentService.list(userId));
    }

    /**
     * 获取文档分析结果
     */
    @GetMapping("/{id}/analysis")
    public Result<DocumentAnalysisVO> getAnalysis(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(documentService.getAnalysis(userId, id));
    }

    /**
     * 触发文档分析
     */
    @PostMapping("/{id}/analyze")
    public Result<DocumentAnalysisVO> analyze(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(documentService.analyze(userId, id));
    }

    /**
     * 双合同比对
     * <p>
     * 对比两份合同的差异条款与风险变化，v1.5.0 新增。
     *
     * @param id     合同 A 文档 ID
     * @param docIdB 合同 B 文档 ID（query 参数）
     * @return 比对结果
     */
    @PostMapping("/{id}/compare")
    public Result<ContractCompareVO> compare(@PathVariable Long id,
                                             @RequestParam("docIdB") Long docIdB) {
        Long userId = requireLogin();
        return Result.success(documentService.compare(userId, id, docIdB));
    }

    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
