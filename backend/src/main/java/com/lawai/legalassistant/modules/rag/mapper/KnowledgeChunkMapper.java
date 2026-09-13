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
     * 批量插入切片（v1.18 V1-13）：多行 VALUES 单语句写入，替代逐条 insertChunk——
     * 大文档场景下数万次单条往返是入库延迟的主要来源。
     *
     * @param chunks 切片实体列表（embedding 为 pgvector 字符串）
     * @return 影响行数
     */
    @Insert("<script>" +
            "INSERT INTO knowledge_chunk(doc_id, chunk_index, content, embedding, created_at) VALUES " +
            "<foreach collection='list' item='c' separator=','>" +
            "(#{c.docId}, #{c.chunkIndex}, #{c.content}, CAST(#{c.embedding} AS vector), NOW())" +
            "</foreach>" +
            "</script>")
    int insertChunks(@Param("list") List<KnowledgeChunk> chunks);

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
            "WHERE c.embedding IS NOT NULL " +
            // v1.18（审查 P2#11）：公共知识 + 本人私有文档——此前硬编码 owner_type='PUBLIC'，
            // 用户上传的 PRIVATE 文档被切片向量化后永远不会被任何检索命中（入库即沉没）
            "  AND (d.owner_type = 'PUBLIC' OR (d.owner_type = 'PRIVATE' AND d.owner_id = #{ownerId})) " +
            "ORDER BY c.embedding <=> CAST(#{vec} AS vector) " +
            "LIMIT #{topK}")
    List<RetrievedChunk> searchByVector(@Param("vec") String vectorStr, @Param("topK") int topK,
                                        @Param("ownerId") Long ownerId);

    /**
     * 关键字文本检索（v1.12.0 检索套件，不依赖 embedding）
     * <p>
     * 对切片内容做 ILIKE 模糊匹配，关联文档取标题与来源。
     * 用于 embedding 不可用时的降级检索，以及复合查询中
     * "findFiles / grepFile" 类的精确关键词定位。
     *
     * @param keyword 关键字（已转义 %/_）
     * @param limit   返回条数
     * @return 检索结果列表
     */
    @Select("SELECT c.id, c.doc_id, d.title, d.source, c.content AS snippet, " +
            "0.0 AS score " +
            "FROM knowledge_chunk c " +
            "JOIN knowledge_doc d ON c.doc_id = d.id " +
            // v1.18（审查 P2#11）：同 searchByVector，私人文档按归属放行
            "WHERE (d.owner_type = 'PUBLIC' OR (d.owner_type = 'PRIVATE' AND d.owner_id = #{ownerId})) " +
            "  AND c.content ILIKE '%' || #{keyword} || '%' " +
            "ORDER BY d.created_at DESC, c.chunk_index ASC " +
            "LIMIT #{limit}")
    List<RetrievedChunk> searchByKeywordText(@Param("keyword") String keyword, @Param("limit") int limit,
                                             @Param("ownerId") Long ownerId);
}
