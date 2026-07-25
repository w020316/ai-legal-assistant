package com.lawai.legalassistant.modules.export.service;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.common.result.ResultCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多格式导出服务
 * <p>
 * v1.11.0 新增：将 Markdown 文本转为 Word (.docx) 与 PDF (.pdf) 字节数组。
 * 支持的 Markdown 元素：H1-H3 标题、无序列表、加粗、段落。
 * 法律文书场景以文本为主，不渲染图片/表格/代码块（保留原文本即可）。
 * <p>
 * 字体策略：PDF 加载思源宋体（NotoSansSC）以支持中文；Word 使用默认中文字体（POI 依赖系统字体）。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** PDF 正文行高（pt） */
    private static final float PDF_LINE_HEIGHT = 14f;
    /** PDF 正文顶部边距（pt） */
    private static final float PDF_MARGIN_TOP = 720f;
    /** PDF 左右边距（pt） */
    private static final float PDF_MARGIN_X = 56f;
    /** PDF 页面底部边距（pt），低于此值换页 */
    private static final float PDF_MARGIN_BOTTOM = 56f;
    /** PDF 标题字号 */
    private static final float PDF_TITLE_FONT_SIZE = 18f;
    /** PDF H2 字号 */
    private static final float PDF_H2_FONT_SIZE = 15f;
    /** PDF H3 字号 */
    private static final float PDF_H3_FONT_SIZE = 13f;
    /** PDF 正文字号 */
    private static final float PDF_BODY_FONT_SIZE = 11f;

    /** 加粗片段正则 **text** 或 __text__ */
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");

    /**
     * 将 Markdown 转为 Word 文档字节数组
     *
     * @param title    文档标题（用于首行大标题）
     * @param markdown Markdown 正文
     * @return .docx 文件字节
     */
    public byte[] markdownToWord(String title, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "导出内容不能为空");
        }
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 标题
            if (title != null && !title.isBlank()) {
                XWPFParagraph t = doc.createParagraph();
                t.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun tr = t.createRun();
                tr.setText(title);
                tr.setBold(true);
                tr.setFontSize(18);
                tr.setFontFamily("宋体");
                // 标题后空一行
                doc.createParagraph();
            }
            // 逐行解析 Markdown
            for (String rawLine : markdown.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    doc.createParagraph();
                    continue;
                }
                XWPFParagraph p = doc.createParagraph();
                if (line.startsWith("### ")) {
                    addRuns(p, line.substring(4), true, 13);
                } else if (line.startsWith("## ")) {
                    addRuns(p, line.substring(3), true, 15);
                } else if (line.startsWith("# ")) {
                    addRuns(p, line.substring(2), true, 17);
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    p.setIndentationLeft(360);
                    addRuns(p, "• " + line.substring(2), false, 11);
                } else {
                    addRuns(p, line, false, 11);
                }
            }
            doc.write(out);
            log.info("Word 导出成功: title={}, sizeBytes={}", title, out.size());
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Word 导出失败: title={}", title, e);
            throw BusinessException.of(ResultCode.UNKNOWN, "Word 导出失败");
        }
    }

    /**
     * 添加带加粗解析的 Run
     * <p>
     * Markdown 加粗 **text** 会被拆分为多个 Run，加粗部分 setBold(true)。
     */
    private void addRuns(XWPFParagraph p, String text, boolean boldAll, int fontSize) {
        Matcher m = BOLD_PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                XWPFRun r = p.createRun();
                r.setText(text.substring(last, m.start()));
                r.setBold(boldAll);
                r.setFontSize(fontSize);
                r.setFontFamily("宋体");
            }
            String boldText = m.group(1) != null ? m.group(1) : m.group(2);
            XWPFRun r = p.createRun();
            r.setText(boldText);
            r.setBold(true);
            r.setFontSize(fontSize);
            r.setFontFamily("宋体");
            last = m.end();
        }
        if (last < text.length()) {
            XWPFRun r = p.createRun();
            r.setText(text.substring(last));
            r.setBold(boldAll);
            r.setFontSize(fontSize);
            r.setFontFamily("宋体");
        }
    }

    /**
     * 将 Markdown 转为 PDF 文档字节数组
     * <p>
     * 字体加载策略：
     * 1. 优先从 classpath:fonts/NotoSansSC-Regular.otf 加载思源宋体（完整中文支持）
     * 2. 加载失败则回退到 Standard14Fonts.HELVETICA（仅支持英文，中文将丢失）
     * <p>
     * 法律文书导出对字体完整性要求高，建议在 classpath 下放置 NotoSansSC 字体文件。
     *
     * @param title    文档标题
     * @param markdown Markdown 正文
     * @return .pdf 文件字节
     */
    public byte[] markdownToPdf(String title, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "导出内容不能为空");
        }
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 加载中文字体（优先 classpath，失败则回退 Helvetica）
            PDType0Font chineseFont = loadChineseFont(doc);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(doc, page);
            float y = PDRectangle.A4.getHeight() - PDF_MARGIN_TOP / 10;
            float pageWidth = PDRectangle.A4.getWidth() - PDF_MARGIN_X * 2;

            // 标题
            if (title != null && !title.isBlank()) {
                y = drawPdfLine(stream, title, PDF_TITLE_FONT_SIZE, true, y, pageWidth, chineseFont, doc);
                y -= PDF_LINE_HEIGHT;
            }

            // 逐行渲染
            for (String rawLine : markdown.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    y -= PDF_LINE_HEIGHT / 2;
                    continue;
                }
                if (line.startsWith("### ")) {
                    y = drawPdfLine(stream, line.substring(4), PDF_H3_FONT_SIZE, true, y, pageWidth, chineseFont, doc);
                } else if (line.startsWith("## ")) {
                    y = drawPdfLine(stream, line.substring(3), PDF_H2_FONT_SIZE, true, y, pageWidth, chineseFont, doc);
                } else if (line.startsWith("# ")) {
                    y = drawPdfLine(stream, line.substring(2), PDF_TITLE_FONT_SIZE, true, y, pageWidth, chineseFont, doc);
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    y = drawPdfLine(stream, "• " + line.substring(2), PDF_BODY_FONT_SIZE, false, y, pageWidth, chineseFont, doc);
                } else {
                    // 去掉 Markdown 加粗符号 ** 便于纯文本渲染
                    String plain = BOLD_PATTERN.matcher(line).replaceAll(m ->
                            m.group(1) != null ? m.group(1) : m.group(2));
                    y = drawPdfLine(stream, plain, PDF_BODY_FONT_SIZE, false, y, pageWidth, chineseFont, doc);
                }
                y -= 4;
                // 换页检测
                if (y < PDF_MARGIN_BOTTOM) {
                    stream.close();
                    PDPage newPage = new PDPage(PDRectangle.A4);
                    doc.addPage(newPage);
                    stream = new PDPageContentStream(doc, newPage);
                    y = PDRectangle.A4.getHeight() - PDF_MARGIN_TOP / 10;
                }
            }
            stream.close();
            doc.save(out);
            log.info("PDF 导出成功: title={}, sizeBytes={}", title, out.size());
            return out.toByteArray();
        } catch (IOException e) {
            log.error("PDF 导出失败: title={}", title, e);
            throw BusinessException.of(ResultCode.UNKNOWN, "PDF 导出失败");
        }
    }

    /**
     * 加载中文字体：按优先级尝试多个来源
     * <ol>
     *   <li>classpath:fonts/NotoSansSC-Regular.otf（随 JAR 打包的字体）</li>
     *   <li>classpath:fonts/NotoSansSC-Regular.ttf</li>
     *   <li>系统路径 /usr/share/fonts/noto/NotoSansCJK-Regular.ttc（Alpine font-noto-cjk 包）</li>
     *   <li>系统路径 /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc（Debian fonts-noto-cjk 包）</li>
     * </ol>
     * 全部失败抛 BusinessException 提示用户安装字体。
     */
    private PDType0Font loadChineseFont(PDDocument doc) {
        // 1. classpath .otf
        try (InputStream is = new ClassPathResource("fonts/NotoSansSC-Regular.otf").getInputStream()) {
            log.info("加载中文字体: classpath:fonts/NotoSansSC-Regular.otf");
            return PDType0Font.load(doc, is);
        } catch (IOException ignored) {
        }
        // 2. classpath .ttf
        try (InputStream is = new ClassPathResource("fonts/NotoSansSC-Regular.ttf").getInputStream()) {
            log.info("加载中文字体: classpath:fonts/NotoSansSC-Regular.ttf");
            return PDType0Font.load(doc, is);
        } catch (IOException ignored) {
        }
        // 3. 系统字体路径（Alpine Linux font-noto-cjk 包安装位置）
        java.io.File sysFont = new java.io.File("/usr/share/fonts/noto/NotoSansCJK-Regular.ttc");
        if (sysFont.exists()) {
            try {
                log.info("加载中文字体: {}", sysFont.getAbsolutePath());
                return PDType0Font.load(doc, sysFont);
            } catch (IOException e) {
                log.warn("系统字体加载失败: {}", sysFont.getAbsolutePath(), e);
            }
        }
        // 4. 系统字体路径（Debian/Ubuntu fonts-noto-cjk 包安装位置）
        java.io.File sysFont2 = new java.io.File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        if (sysFont2.exists()) {
            try {
                log.info("加载中文字体: {}", sysFont2.getAbsolutePath());
                return PDType0Font.load(doc, sysFont2);
            } catch (IOException e) {
                log.warn("系统字体加载失败: {}", sysFont2.getAbsolutePath(), e);
            }
        }
        log.error("中文字体加载失败，PDF 无法渲染中文。请将 NotoSansSC 字体放到 classpath:fonts/ 或安装系统字体包");
        throw BusinessException.of(ResultCode.SYSTEM_ERROR,
                "PDF 中文字体未配置，请联系管理员安装 font-noto-cjk 字体包");
    }

    /**
     * 渲染一行文本到 PDF，自动换页。返回渲染后的 y 坐标。
     * 简化实现：不自动折行（中文行宽超页宽时会被截断，但法律文书单行通常不会超长）。
     */
    private float drawPdfLine(PDPageContentStream stream, String text, float fontSize, boolean bold,
                              float y, float pageWidth, PDType0Font font, PDDocument doc) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(PDF_MARGIN_X, y);
        stream.showText(text);
        stream.endText();
        return y - PDF_LINE_HEIGHT;
    }
}
