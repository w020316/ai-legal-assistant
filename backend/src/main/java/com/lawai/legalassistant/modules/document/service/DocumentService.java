package com.lawai.legalassistant.modules.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.ai.prompt.PromptTemplates;
import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.util.FileTypeValidator;
import com.lawai.legalassistant.modules.document.dto.ContractCompareVO;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    @Value("${lawai.upload.path:./uploads}")
    private String uploadPath;

    public DocumentService(UserDocumentMapper documentMapper, AiRouter aiRouter,
                           ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.aiRouter = aiRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传文档：读取字节 → magic bytes 校验 → 保存文件 → 解析文本 → 入库
     * <p>
     * v1.7.0 新增 magic bytes 校验，防止伪造扩展名上传恶意文件。
     * 图片类型（JPG/PNG）使用 AI Vision 识别文字，v1.6.0 新增。
     *
     * @param userId 用户 ID
     * @param file   上传文件
     * @return 上传响应
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();

        try {
            byte[] bytes = file.getBytes();

            // 1. magic bytes 校验：先读字节再校验真实文件类型，防止伪造扩展名
            String fileType = FileTypeValidator.validate(originalFilename, bytes);

            // 2. 保存文件到本地
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

            // 3. 解析文本：图片类型用 AI Vision 识别，其他类型用本地解析
            String text = parseText(fileType, bytes, originalFilename);

            // 4. 入库
            UserDocument doc = new UserDocument();
            doc.setUserId(userId);
            doc.setFilename(originalFilename);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setOcrText(text);
            documentMapper.insert(doc);

            log.info("文档上传成功: userId={}, docId={}, filename={}, fileType={}", userId, doc.getId(), originalFilename, fileType);
            return new UploadResponse(doc.getId(), originalFilename, fileType, file.getSize());
        } catch (IOException e) {
            log.error("文档上传失败", e);
            throw BusinessException.of(ResultCode.UNKNOWN, "文件读取失败");
        }
    }

    /**
     * 触发文档分析
     * <p>
     * v1.8.0 新增缓存：若文档已有分析结果则直接返回，跳过 AI 调用，节省成本。
     * 如需重新分析，前端应先删除文档再重新上传，或后续提供"重新分析"接口。
     *
     * @param userId 用户 ID
     * @param docId  文档 ID
     * @return 分析结果
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentAnalysisVO analyze(Long userId, Long docId) {
        UserDocument doc = getOwnedDocument(userId, docId);
        if (doc.getOcrText() == null || doc.getOcrText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文档文本为空，无法分析");
        }

        // 缓存命中：已有分析结果则直接返回，跳过 AI 调用（v1.8.0 新增）
        if (doc.getAnalysisResult() != null && !doc.getAnalysisResult().isBlank()) {
            try {
                DocumentAnalysisVO cached = objectMapper.readValue(doc.getAnalysisResult(), DocumentAnalysisVO.class);
                log.info("文档分析命中缓存: docId={}, riskPoints={}", docId,
                        cached.getRiskPoints() != null ? cached.getRiskPoints().size() : 0);
                return cached;
            } catch (Exception e) {
                log.warn("缓存分析结果解析失败，重新调用 AI: docId={}", docId);
            }
        }

        try {
            // 1. 调用 AI 分析
            String aiResponse = aiRouter.chat(
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
     * 重新分析：清除已有分析结果后强制重新调用 AI（v1.9.0 新增）
     * <p>
     * 与 analyze 的区别：analyze 命中缓存直接返回，reanalyze 先清除缓存再调用 AI。
     * 适用于文档内容变更后需要刷新分析结果，或用户对旧分析不满意希望重新生成。
     *
     * @param userId 用户 ID
     * @param docId  文档 ID
     * @return 新的分析结果
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentAnalysisVO reanalyze(Long userId, Long docId) {
        // 1. 校验文档归属
        UserDocument doc = getOwnedDocument(userId, docId);
        if (doc.getOcrText() == null || doc.getOcrText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文档文本为空，无法分析");
        }

        // 2. 清除旧分析结果，确保 analyze 不会命中缓存
        documentMapper.clearAnalysisResult(docId);
        log.info("文档重新分析：已清除旧分析结果 docId={}", docId);

        // 3. 重新调用 AI 分析（此时 analysisResult 已为 null，不会命中缓存）
        return analyze(userId, docId);
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

    /**
     * 双合同比对
     * <p>
     * 对比两份合同的差异条款与风险变化，v1.5.0 新增。
     *
     * @param userId 用户 ID
     * @param docIdA 合同 A 文档 ID
     * @param docIdB 合同 B 文档 ID
     * @return 比对结果
     */
    public ContractCompareVO compare(Long userId, Long docIdA, Long docIdB) {
        if (docIdA.equals(docIdB)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "不能与同一份合同进行比对");
        }
        UserDocument docA = getOwnedDocument(userId, docIdA);
        UserDocument docB = getOwnedDocument(userId, docIdB);
        if (docA.getOcrText() == null || docA.getOcrText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "合同 A 文本为空，无法比对");
        }
        if (docB.getOcrText() == null || docB.getOcrText().isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "合同 B 文本为空，无法比对");
        }

        try {
            // 拼装用户消息：明确标注合同 A 与合同 B
            String userMessage = "【合同 A】\n" + docA.getOcrText() + "\n\n【合同 B】\n" + docB.getOcrText();
            String aiResponse = aiRouter.chat(PromptTemplates.CONTRACT_COMPARE_SYSTEM, userMessage);
            String json = extractJson(aiResponse);
            ContractCompareVO result = objectMapper.readValue(json, ContractCompareVO.class);
            result.setDocIdA(docIdA);
            result.setDocIdB(docIdB);
            log.info("双合同比对完成: docIdA={}, docIdB={}, diffs={}",
                    docIdA, docIdB,
                    result.getDiffs() != null ? result.getDiffs().size() : 0);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("双合同比对失败: docIdA={}, docIdB={}", docIdA, docIdB, e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "合同比对失败，请稍后重试");
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
     * 解析文本
     * <p>
     * 图片类型使用 AI Vision 识别文字，其他类型用本地解析，v1.6.0 新增图片支持。
     * 文件类型校验已由 FileTypeValidator.validate 在 upload 阶段完成。
     */
    private String parseText(String fileType, byte[] bytes, String originalFilename) throws IOException {
        return switch (fileType) {
            case "PDF" -> parsePdf(bytes);
            case "DOCX" -> parseDocx(bytes);
            case "TXT" -> new String(bytes, StandardCharsets.UTF_8);
            case "JPG" -> ocrImage(bytes, "image/jpeg");
            case "PNG" -> ocrImage(bytes, "image/png");
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    /**
     * 图片 OCR 文字识别（AI Vision）
     * <p>
     * 调用 AiRouter.chatWithImage 识别图片中的文字，v1.6.0 新增。
     */
    private String ocrImage(byte[] imageBytes, String mimeType) {
        try {
            String text = aiRouter.chatWithImage(
                    PromptTemplates.IMAGE_OCR_SYSTEM,
                    PromptTemplates.IMAGE_OCR_USER,
                    imageBytes,
                    mimeType);
            if (text == null || text.isBlank()) {
                throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "图片文字识别结果为空，请确认图片清晰度");
            }
            log.info("图片 OCR 识别成功: mimeType={}, textLength={}", mimeType, text.length());
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片 OCR 识别失败", e);
            throw BusinessException.of(ResultCode.AI_SERVICE_ERROR, "图片文字识别失败，请稍后重试");
        }
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
