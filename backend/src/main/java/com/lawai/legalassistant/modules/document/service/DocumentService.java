package com.lawai.legalassistant.modules.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AgnesClient;
import com.lawai.legalassistant.ai.prompt.PromptTemplates;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.modules.document.dto.DocumentAnalysisVO;
import com.lawai.legalassistant.modules.document.dto.UploadResponse;
import com.lawai.legalassistant.modules.document.entity.UserDocument;
import com.lawai.legalassistant.modules.document.mapper.UserDocumentMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务
 * <p>
 * 提供文档上传解析、AI 风险分析、列表与详情查询能力，
 * 对应设计方案 5.2 节文档上传与风险分析。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final UserDocumentMapper documentMapper;
    private final AgnesClient agnesClient;
    private final ObjectMapper objectMapper;

    @Value("${lawai.upload.path:./uploads}")
    private String uploadPath;

    public DocumentService(UserDocumentMapper documentMapper, AgnesClient agnesClient,
                           ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.agnesClient = agnesClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传文档：保存文件 → 解析文本 → 入库
     *
     * @param userId 用户 ID
     * @param file   上传文件
     * @return 上传响应
     */
    public UploadResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = detectFileType(originalFilename);

        try {
            byte[] bytes = file.getBytes();

            // 1. 保存文件到本地
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String storedFilename = UUID.randomUUID().toString() + ext;
            Path filePath = uploadDir.resolve(storedFilename);
            Files.write(filePath, bytes);

            // 2. 解析文本
            String text = parseText(fileType, bytes);

            // 3. 入库
            UserDocument doc = new UserDocument();
            doc.setUserId(userId);
            doc.setFilename(originalFilename);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setOcrText(text);
            documentMapper.insert(doc);

            log.info("文档上传成功: userId={}, docId={}, filename={}", userId, doc.getId(), originalFilename);
            return new UploadResponse(doc.getId(), originalFilename, fileType, file.getSize());
        } catch (IOException e) {
            log.error("文档上传失败", e);
            throw BusinessException.of(ResultCode.UNKNOWN, "文件读取失败");
        }
    }

    /**
     * 触发文档分析
     *
     * @param userId 用户 ID
     * @param docId  文档 ID
     * @return 分析结果
     */
    public DocumentAnalysisVO analyze(Long userId, Long docId) {
        UserDocument doc = getOwnedDocument(userId, docId);
        if (doc.getOcrText() == null || doc.getOcrText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文档文本为空，无法分析");
        }

        try {
            // 1. 调用 AI 分析
            String aiResponse = agnesClient.chat(
                    PromptTemplates.CONTRACT_REVIEW_SYSTEM,
                    doc.getOcrText());

            // 2. 提取 JSON 并解析
            String json = extractJson(aiResponse);
            DocumentAnalysisVO analysis = objectMapper.readValue(json, DocumentAnalysisVO.class);

            // 3. 更新数据库
            documentMapper.updateAnalysisResult(docId, json);

            log.info("文档分析完成: docId={}, riskPoints={}", docId,
                    analysis.getRiskPoints() != null ? analysis.getRiskPoints().size() : 0);
            return analysis;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档分析失败: docId={}", docId, e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "文档分析失败，请稍后重试");
        }
    }

    /**
     * 用户文档列表
     *
     * @param userId 用户 ID
     * @return 文档列表
     */
    public List<UserDocument> list(Long userId) {
        return documentMapper.selectList(
                new LambdaQueryWrapper<UserDocument>()
                        .eq(UserDocument::getUserId, userId)
                        .orderByDesc(UserDocument::getCreatedAt));
    }

    /**
     * 获取分析结果
     *
     * @param userId 用户 ID
     * @param docId  文档 ID
     * @return 分析结果，未分析时返回 null
     */
    public DocumentAnalysisVO getAnalysis(Long userId, Long docId) {
        UserDocument doc = getOwnedDocument(userId, docId);
        if (doc.getAnalysisResult() == null || doc.getAnalysisResult().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(doc.getAnalysisResult(), DocumentAnalysisVO.class);
        } catch (Exception e) {
            log.warn("解析分析结果失败: docId={}", docId, e);
            return null;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 获取文档（校验归属）
     */
    private UserDocument getOwnedDocument(Long userId, Long docId) {
        UserDocument doc = documentMapper.selectById(docId);
        if (doc == null || !userId.equals(doc.getUserId())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "文档不存在或无权限");
        }
        return doc;
    }

    /**
     * 检测文件类型
     */
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

    /**
     * 解析文本
     */
    private String parseText(String fileType, byte[] bytes) throws IOException {
        return switch (fileType) {
            case "PDF" -> parsePdf(bytes);
            case "DOCX" -> parseDocx(bytes);
            case "TXT" -> new String(bytes, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    /**
     * PDF 解析（PDFBox 3.x API）
     */
    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * DOCX 解析（POI XWPF）
     */
    private String parseDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 从 AI 响应中提取 JSON（去除 markdown 代码块包裹）
     */
    private String extractJson(String text) {
        if (text == null) {
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "AI 返回内容为空");
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "AI 返回格式异常");
        }
        return trimmed;
    }
}
