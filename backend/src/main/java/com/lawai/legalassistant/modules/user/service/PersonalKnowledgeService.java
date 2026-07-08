package com.lawai.legalassistant.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeDocMapper;
import com.lawai.legalassistant.modules.rag.service.RagService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 个人知识库服务
 * <p>
 * 复用 RagService 进行文档入库（owner_type=PRIVATE），实现用户私有文档的
 * 上传向量化、列表查询与删除，对应设计方案 US-F02。
 */
@Service
public class PersonalKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(PersonalKnowledgeService.class);

    private final RagService ragService;
    private final KnowledgeDocMapper knowledgeDocMapper;

    public PersonalKnowledgeService(RagService ragService, KnowledgeDocMapper knowledgeDocMapper) {
        this.ragService = ragService;
        this.knowledgeDocMapper = knowledgeDocMapper;
    }

    /**
     * 上传个人知识库文档
     * <p>
     * 解析文件文本 → 调用 RagService.ingestDocument 入库（私有归属）
     *
     * @param userId 用户 ID
     * @param file   上传文件
     * @return 文档 ID
     */
    public Long upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = detectFileType(originalFilename);

        try {
            byte[] bytes = file.getBytes();
            String text = parseText(fileType, bytes);
            if (text == null || text.isBlank()) {
                throw BusinessException.of(ResultCode.PARAM_ERROR, "文档文本为空");
            }

            Long docId = ragService.ingestDocument(
                    originalFilename,
                    "USER_UPLOAD",
                    "个人上传",
                    text,
                    "PRIVATE",
                    userId);

            log.info("个人知识库文档上传: userId={}, docId={}", userId, docId);
            return docId;
        } catch (IOException e) {
            log.error("个人知识库文档上传失败", e);
            throw BusinessException.of(ResultCode.UNKNOWN, "文件读取失败");
        }
    }

    /**
     * 个人知识库文档列表
     *
     * @param userId 用户 ID
     * @return 文档列表（不含 rawText）
     */
    public List<KnowledgeDoc> list(Long userId) {
        return knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .select(KnowledgeDoc::getId, KnowledgeDoc::getTitle,
                                KnowledgeDoc::getSource, KnowledgeDoc::getStatus,
                                KnowledgeDoc::getCreatedAt)
                        .eq(KnowledgeDoc::getOwnerType, "PRIVATE")
                        .eq(KnowledgeDoc::getOwnerId, userId)
                        .orderByDesc(KnowledgeDoc::getCreatedAt));
    }

    /**
     * 删除个人知识库文档
     *
     * @param userId 用户 ID
     * @param docId  文档 ID
     */
    public void delete(Long userId, Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || !"PRIVATE".equals(doc.getOwnerType()) || !userId.equals(doc.getOwnerId())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在或无权限");
        }
        knowledgeDocMapper.deleteById(docId);
        log.info("个人知识库文档删除: userId={}, docId={}", userId, docId);
    }

    // ==================== 内部方法 ====================

    private String detectFileType(String filename) {
        if (filename == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件名不能为空");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".docx")) {
            return "DOCX";
        }
        if (lower.endsWith(".txt")) {
            return "TXT";
        }
        throw BusinessException.of(ResultCode.PARAM_ERROR, "不支持的文件类型，仅支持 PDF/DOCX/TXT");
    }

    private String parseText(String fileType, byte[] bytes) throws IOException {
        return switch (fileType) {
            case "PDF" -> parsePdf(bytes);
            case "DOCX" -> parseDocx(bytes);
            case "TXT" -> new String(bytes, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }
}
