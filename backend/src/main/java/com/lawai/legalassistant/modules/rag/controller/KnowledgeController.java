package com.lawai.legalassistant.modules.rag.controller;

import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.modules.rag.dto.IngestRequest;
import com.lawai.legalassistant.modules.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库管理 Controller（管理员接口）
 * <p>
 * 提供文档入库接口，供管理员维护公共法律知识库。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeController {

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 单文档入库（切片 → 向量化 → 写库）
     *
     * @param req 入库请求
     * @return 文档 ID
     */
    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingest(@Valid @RequestBody IngestRequest req) {
        Long docId = ragService.ingestDocument(
                req.getTitle(),
                req.getDocType(),
                req.getSource(),
                req.getRawText(),
                req.getOwnerType(),
                req.getOwnerId());
        return Result.success(Map.of("docId", docId));
    }
}
