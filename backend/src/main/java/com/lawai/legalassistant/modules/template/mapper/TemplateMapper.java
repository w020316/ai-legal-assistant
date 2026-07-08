package com.lawai.legalassistant.modules.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawai.legalassistant.modules.template.dto.TemplateVO;
import com.lawai.legalassistant.modules.template.entity.LegalTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模板 Mapper
 * <p>
 * 复用 knowledge_doc 表，查询条件 doc_type='TEMPLATE'，
 * category 字段从 metadata JSONB 提取。
 */
public interface TemplateMapper extends BaseMapper<LegalTemplate> {

    /**
     * 按分类查询模板列表
     *
     * @param category 分类，null 时查全部
     * @return 模板列表
     */
    @Select("""
            <script>
            SELECT id, title, source, raw_text,
                   metadata->>'category' AS category
            FROM knowledge_doc
            WHERE owner_type = 'PUBLIC' AND doc_type = 'TEMPLATE'
            <if test='category != null and category != ""'>AND metadata->>'category' = #{category}</if>
            ORDER BY created_at DESC
            </script>
            """)
    List<TemplateVO> selectTemplates(@Param("category") String category);

    /**
     * 查询模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @Select("""
            SELECT id, title, source, raw_text,
                   metadata->>'category' AS category
            FROM knowledge_doc
            WHERE id = #{id} AND owner_type = 'PUBLIC' AND doc_type = 'TEMPLATE'
            """)
    TemplateVO selectTemplateById(@Param("id") Long id);
}
