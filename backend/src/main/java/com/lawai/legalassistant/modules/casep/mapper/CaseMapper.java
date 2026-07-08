package com.lawai.legalassistant.modules.casep.mapper;

import com.lawai.legalassistant.modules.casep.dto.CaseVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 案例检索 Mapper
 * <p>
 * 基于 knowledge_doc（doc_type='CASE'）+ knowledge_chunk 联合检索，
 * 案由/法院/年份等元数据存储于 knowledge_doc.metadata JSONB 中。
 */
public interface CaseMapper {

    /**
     * 向量检索 + 元数据过滤
     * <p>
     * 关键词向量化后，结合元数据条件在 pgvector 中检索 Top-K 相关案例片段。
     *
     * @param vec        关键词向量字符串 [v1,v2,...]
     * @param cause      案由（可选）
     * @param courtLevel 法院层级（可选）
     * @param year       审理年份（可选）
     * @param topK       返回条数
     * @return 案例列表
     */
    @Select("""
            <script>
            SELECT d.id, d.title, d.source,
                   d.metadata->>'cause' AS case_cause,
                   d.metadata->>'court_level' AS court,
                   (d.metadata->>'year')::int AS year,
                   c.content AS summary,
                   (1 - (c.embedding &lt;=&gt; CAST(#{vec} AS vector))) AS score
            FROM knowledge_chunk c
            JOIN knowledge_doc d ON c.doc_id = d.id
            WHERE d.owner_type = 'PUBLIC' AND d.doc_type = 'CASE' AND c.embedding IS NOT NULL
            <if test='cause != null and cause != ""'>AND d.metadata->>'cause' = #{cause}</if>
            <if test='courtLevel != null and courtLevel != ""'>AND d.metadata->>'court_level' = #{courtLevel}</if>
            <if test='year != null'>AND (d.metadata->>'year')::int = #{year}</if>
            ORDER BY c.embedding &lt;=&gt; CAST(#{vec} AS vector)
            LIMIT #{topK}
            </script>
            """)
    List<CaseVO> searchByVectorWithFilters(@Param("vec") String vec,
                                           @Param("cause") String cause,
                                           @Param("courtLevel") String courtLevel,
                                           @Param("year") Integer year,
                                           @Param("topK") int topK);

    /**
     * 元数据分页检索（无关键词）
     *
     * @param cause      案由（可选）
     * @param courtLevel 法院层级（可选）
     * @param year       审理年份（可选）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 案例列表
     */
    @Select("""
            <script>
            SELECT d.id, d.title, d.source,
                   d.metadata->>'cause' AS case_cause,
                   d.metadata->>'court_level' AS court,
                   (d.metadata->>'year')::int AS year,
                   d.metadata->>'summary' AS summary
            FROM knowledge_doc d
            WHERE d.owner_type = 'PUBLIC' AND d.doc_type = 'CASE'
            <if test='cause != null and cause != ""'>AND d.metadata->>'cause' = #{cause}</if>
            <if test='courtLevel != null and courtLevel != ""'>AND d.metadata->>'court_level' = #{courtLevel}</if>
            <if test='year != null'>AND (d.metadata->>'year')::int = #{year}</if>
            ORDER BY d.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<CaseVO> searchByMetadata(@Param("cause") String cause,
                                  @Param("courtLevel") String courtLevel,
                                  @Param("year") Integer year,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /**
     * 元数据检索总数（无关键词时分页用）
     *
     * @param cause      案由（可选）
     * @param courtLevel 法院层级（可选）
     * @param year       审理年份（可选）
     * @return 总数
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM knowledge_doc d
            WHERE d.owner_type = 'PUBLIC' AND d.doc_type = 'CASE'
            <if test='cause != null and cause != ""'>AND d.metadata->>'cause' = #{cause}</if>
            <if test='courtLevel != null and courtLevel != ""'>AND d.metadata->>'court_level' = #{courtLevel}</if>
            <if test='year != null'>AND (d.metadata->>'year')::int = #{year}</if>
            </script>
            """)
    long countByMetadata(@Param("cause") String cause,
                         @Param("courtLevel") String courtLevel,
                         @Param("year") Integer year);
}
