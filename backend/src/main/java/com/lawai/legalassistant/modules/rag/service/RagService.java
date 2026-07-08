package com.lawai.legalassistant.modules.rag.service;

import com.lawai.legalassistant.ai.client.AgnesClient;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeChunkMapper;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeDocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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

    private final AgnesClient agnesClient;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public RagService(AgnesClient agnesClient, KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper) {
        this.agnesClient = agnesClient;
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
            float[] vec = agnesClient.embed(question);
            String vecStr = toPgVector(vec);
            int k = topK > 0 ? topK : TOP_K_DEFAULT;
            return chunkMapper.searchByVector(vecStr, k);
        } catch (Exception e) {
            log.error("RAG 检索失败: question={}", question, e);
            return Collections.emptyList();
        }
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
            float[] emb = agnesClient.embed(chunk);
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
