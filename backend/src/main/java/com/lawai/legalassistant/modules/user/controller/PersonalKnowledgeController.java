package com.lawai.legalassistant.modules.user.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
import com.lawai.legalassistant.modules.user.service.PersonalKnowledgeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 个人知识库 Controller
 * <p>
 * 提供个人私有文档的上传、列表、删除接口，对应设计方案 US-F02。
 */
@RestController
@RequestMapping("/api/v1/knowledge/personal")
public class PersonalKnowledgeController {

    private final PersonalKnowledgeService personalKnowledgeService;

    public PersonalKnowledgeController(PersonalKnowledgeService personalKnowledgeService) {
        this.personalKnowledgeService = personalKnowledgeService;
    }

    /**
     * 上传个人知识库文档（multipart/form-data）
     *
     * @param file 文件
     * @return 文档 ID
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Long userId = requireLogin();
        Long docId = personalKnowledgeService.upload(userId, file);
        return Result.success(Map.of("docId", docId));
    }

    /**
     * 个人知识库文档列表
     */
    @GetMapping
    public Result<List<KnowledgeDoc>> list() {
        Long userId = requireLogin();
        return Result.success(personalKnowledgeService.list(userId));
    }

    /**
     * 删除个人知识库文档
     *
     * @param docId 文档 ID
     */
    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable Long docId) {
        Long userId = requireLogin();
        personalKnowledgeService.delete(userId, docId);
        return Result.success();
    }

    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
