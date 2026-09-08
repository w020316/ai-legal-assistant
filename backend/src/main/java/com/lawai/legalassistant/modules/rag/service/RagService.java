package com.lawai.legalassistant.modules.rag.service;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeChunkMapper;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeDocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * RAG 检索增强服务
 * <p>
 * 提供文档入库（切片 → 向量化 → 写库）与相似度检索能力，
 * 对应设计方案 3.7 节 RAG 知识库架构。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** 切片大小（字符数） */
    private static final int CHUNK_SIZE = 500;
    /** 切片重叠（字符数） */
    private static final int CHUNK_OVERLAP = 80;
    /** 默认 Top-K */
    private static final int TOP_K_DEFAULT = 5;

    private final AiRouter aiRouter;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public RagService(AiRouter aiRouter, KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper) {
        this.aiRouter = aiRouter;
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
    }

    /**
     * 检索 Top-K 相关片段
     * <p>
     * 流程：问题向量化 → pgvector 余弦相似检索 → 关联文档取标题来源。
     *
     * @param question 用户问题
     * @param topK     返回条数，&lt;=0 时取默认值 5
     * @return 检索结果列表，检索失败返回空列表
     */
    public List<RetrievedChunk> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }
        try {
            float[] vec = aiRouter.embed(question);
            String vecStr = toPgVector(vec);
            int k = topK > 0 ? topK : TOP_K_DEFAULT;
            return chunkMapper.searchByVector(vecStr, k);
        } catch (Exception e) {
            log.error("RAG 检索失败: question={}", question, e);
            return Collections.emptyList();
        }
    }

    /**
     * 复合深度检索（v1.12.0 检索套件 Retrieval Harness）
     * <p>
     * 多策略融合 + 多步递归：
     * 1. 语义检索：对整句做向量检索（embedding 可用时）；
     * 2. 关键字定位（grepFile）：从问题中提取关键词，逐词对切片做 ILIKE 精确定位；
     * 3. 多来源合并去重：按切片 ID 去重，保留语义与关键词双重命中的结果。
     * <p>
     * 与单次向量检索相比，能显著提升"包含不可抗力条款且违约金比例超过 20%"这类
     * 复合查询的召回率；当 embedding 服务不可用时自动退化为纯关键词检索，仍可用。
     *
     * @param question 用户问题
     * @param topK     返回条数
     * @return 检索结果列表
     */
    public List<RetrievedChunk> retrieveDeep(String question, int topK) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }
        int k = topK > 0 ? topK : TOP_K_DEFAULT;
        // 按关键字段去重，保持稳定顺序
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<RetrievedChunk> merged = new ArrayList<>();

        // 1. 语义检索（embedding 可用时）
        try {
            float[] vec = aiRouter.embed(question);
            List<RetrievedChunk> semantic = chunkMapper.searchByVector(toPgVector(vec), k);
            for (RetrievedChunk c : semantic) {
                if (seen.add(c.getId())) {
                    merged.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("检索套件·语义检索不可用，退化为关键字检索: {}", e.getMessage());
        }

        // 2. 关键字定位（grepFile）：对复合查询的多关键词逐一定位
        for (String kw : extractKeywords(question)) {
            List<RetrievedChunk> hits = chunkMapper.searchByKeywordText(escapeLike(kw), k);
            for (RetrievedChunk c : hits) {
                if (merged.size() >= k) {
                    break;
                }
                if (seen.add(c.getId())) {
                    merged.add(c);
                }
            }
            if (merged.size() >= k) {
                break;
            }
        }

        return merged;
    }

    /**
     * 从问题中提取检索关键词（启发式：按标点/空格切分，取 2 字以上的词）
     * <p>
     * 不依赖 LLM，确定性强、零额外 AI 开销，适合复合查询的多策略召回。
     */
    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        // 先按常见标点与空白切分
        for (String part : question.split("[，。；、,.!?？!；\\s]+")) {
            String kw = part.trim();
            if (kw.length() >= 2 && !KEYWORD_STOP.contains(kw)) {
                keywords.add(kw);
            }
        }
        return keywords;
    }

    /** 常见停用词，避免无效关键字放大检索噪音 */
    private static final java.util.Set<String> KEYWORD_STOP = java.util.Set.of(
            "如何", "什么", "怎么办", "请问", "为什么", "是否", "哪些", "一个", "有没有", "根据");

    /** 转义 ILIKE 通配符，防止用户在问题中注入 %/_ 扩大匹配范围 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 文档入库：写文档主表 → 切片 → 向量化 → 写切片表 → 更新状态
     *
     * @param title     文档标题
     * @param docType   文档类型（LAW/CASE/TEMPLATE/GUIDE/USER_UPLOAD）
     * @param source    来源
     * @param rawText   原文
     * @param ownerType 归属类型（PUBLIC/PRIVATE），默认 PUBLIC
     * @param ownerId   PRIVATE 时为用户 ID
     * @return 文档 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long ingestDocument(String title, String docType, String source, String rawText,
                               String ownerType, Long ownerId) {
        // 1. 写文档主表（status=0 待处理）
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setDocType(docType);
        doc.setSource(source);
        doc.setRawText(rawText);
        doc.setOwnerType(ownerType != null ? ownerType : "PUBLIC");
        doc.setOwnerId(ownerId);
        doc.setStatus(0);
        docMapper.insert(doc);
        Long docId = doc.getId();

        // 2. 切片
        List<String> chunks = splitText(rawText, CHUNK_SIZE, CHUNK_OVERLAP);
        log.info("文档入库: docId={}, title={}, 切片数={}", docId, title, chunks.size());

        // 3. 逐片向量化并入库
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] emb = aiRouter.embed(chunk);
            KnowledgeChunk entity = new KnowledgeChunk();
            entity.setDocId(docId);
            entity.setChunkIndex(i);
            entity.setContent(chunk);
            entity.setEmbedding(toPgVector(emb));
            chunkMapper.insertChunk(entity);
        }

        // 4. 更新文档状态为已向量化
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(docId);
        update.setStatus(1);
        docMapper.updateById(update);

        return docId;
    }

    /**
     * 文本切片：按段落聚合 + 字符数控制 + 重叠
     * <p>
     * 优先按空行分段，将段落聚合到 chunkSize 内；单个段落超长时硬切并保留 overlap 重叠。
     *
     * @param text      原文
     * @param chunkSize 切片大小
     * @param overlap   重叠大小
     * @return 切片列表
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder buffer = new StringBuilder();
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            // 当前段落加入会超长，先保存已有内容
            if (buffer.length() > 0 && buffer.length() + p.length() + 1 > chunkSize) {
                chunks.add(buffer.toString());
                buffer = new StringBuilder(overlap(buffer, overlap));
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(p);
            // 单个段落超长，硬切
            while (buffer.length() > chunkSize) {
                chunks.add(buffer.substring(0, chunkSize));
                int start = Math.max(0, chunkSize - overlap);
                buffer = new StringBuilder(buffer.substring(start));
            }
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    /**
     * 保留 buffer 末尾 overlap 字符作为下一片重叠上下文
     */
    private String overlap(StringBuilder buffer, int overlap) {
        if (buffer.length() <= overlap) {
            return buffer.toString();
        }
        return buffer.substring(buffer.length() - overlap);
    }

    /**
     * float[] → pgvector 字符串 [v1,v2,...]
     */
    private String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
