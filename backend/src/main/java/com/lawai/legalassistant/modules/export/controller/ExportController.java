package com.lawai.legalassistant.modules.export.controller;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import com.lawai.legalassistant.common.utils.SecurityUtil;
import com.lawai.legalassistant.modules.export.dto.ExportRequest;
import com.lawai.legalassistant.modules.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 导出 Controller
 * <p>
 * v1.11.0 新增：通用 Markdown → Word/PDF 导出接口。
 * 路径前缀 /api/v1/export，需登录访问。
 * 前端通过 POST 提交 {title, content}，后端返回二进制文件流。
 */
@Tag(name = "导出", description = "Markdown 转 Word/PDF 导出")
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * 导出为 Word (.docx)
     */
    @Operation(summary = "导出 Word 文档")
    @PostMapping("/word")
    public ResponseEntity<byte[]> exportWord(@Valid @RequestBody ExportRequest req) {
        requireLogin();
        String safeTitle = sanitizeFilename(req.getTitle());
        byte[] data = exportService.markdownToWord(req.getTitle(), req.getContent());
        log.info("Word 导出请求: title={}, sizeBytes={}", req.getTitle(), data.length);
        return fileResponse(data, safeTitle + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /**
     * 导出为 PDF (.pdf)
     */
    @Operation(summary = "导出 PDF 文档")
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody ExportRequest req) {
        requireLogin();
        String safeTitle = sanitizeFilename(req.getTitle());
        byte[] data = exportService.markdownToPdf(req.getTitle(), req.getContent());
        log.info("PDF 导出请求: title={}, sizeBytes={}", req.getTitle(), data.length);
        return fileResponse(data, safeTitle + ".pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    /**
     * 构造文件下载响应：Content-Disposition 使用 RFC 5987 编码处理中文文件名
     */
    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String contentType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    /**
     * 将标题转为安全文件名：去除文件名非法字符，空标题回退为 "linzAI导出"
     */
    private String sanitizeFilename(String title) {
        if (title == null || title.isBlank()) {
            return "linzAI导出";
        }
        return title.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(title.length(), 64));
    }

    private Long requireLogin() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
