package com.lawai.legalassistant.modules.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawai.legalassistant.modules.rag.dto.RetrievedChunk;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识切片 Mapper
 * <p>
 * 向量检索使用 pgvector 的 &lt;=&gt; 余弦距离算子，通过原生 SQL 实现，
 * 结果按距离升序取 Top-K，score = 1 - distance（余弦相似度）。
 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 插入切片（embedding 以 vector 类型写入）
     *
     * @param chunk 切片实体
     * @return 影响行数
     */
    @Insert("INSERT INTO knowledge_chunk(doc_id, chunk_index, content, embedding, created_at) " +
            "VALUES(#{docId}, #{chunkIndex}, #{content}, CAST(#{embedding} AS vector), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChunk(KnowledgeChunk chunk);

    /**
     * 余弦相似向量检索 Top-K
     * <p>
     * 仅检索公共知识库（owner_type='PUBLIC'）且 embedding 非空的切片，
     * 关联 knowledge_doc 取标题与来源。
     *
     * @param vectorStr 向量字符串，格式 [v1,v2,...]
     * @param topK      返回条数
     * @return 检索结果列表
     */
    @Select("SELECT c.id, c.doc_id, d.title, d.source, c.content AS snippet, " +
            "(1 - (c.embedding <=> CAST(#{vec} AS vector))) AS score " +
            "FROM knowledge_chunk c " +
            "JOIN knowledge_doc d ON c.doc_id = d.id " +
            "WHERE d.owner_type = 'PUBLIC' AND c.embedding IS NOT NULL " +
            "ORDER BY c.embedding <=> CAST(#{vec} AS vector) " +
            "LIMIT #{topK}")
    List<RetrievedChunk> searchByVector(@Param("vec") String vectorStr, @Param("topK") int topK);
}
