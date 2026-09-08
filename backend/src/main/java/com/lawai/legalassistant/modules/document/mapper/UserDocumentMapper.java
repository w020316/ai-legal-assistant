package com.lawai.legalassistant.modules.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawai.legalassistant.modules.document.entity.UserDocument;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户文档 Mapper
 * <p>
 * analysis_result 为 JSONB 类型，更新时通过 CAST 转换。
 */
public interface UserDocumentMapper extends BaseMapper<UserDocument> {

    /**
     * 查询同一用户下同名文档的最大版本号（v1.12.0 新增，用于文档版本化）
     *
     * @param userId   用户 ID
     * @param filename 文件名
     * @return 最大版本号；无记录返回 null
     */
    @Select("SELECT MAX(version) FROM user_document WHERE user_id = #{userId} AND filename = #{filename}")
    Long maxVersionByFilename(@Param("userId") Long userId, @Param("filename") String filename);

    /**
     * 更新分析结果（JSONB）
     *
     * @param id              文档 ID
     * @param analysisResult  分析结果 JSON 字符串
     * @return 影响行数
     */
    @Update("UPDATE user_document SET analysis_result = CAST(#{analysisResult} AS jsonb) WHERE id = #{id}")
    int updateAnalysisResult(@Param("id") Long id, @Param("analysisResult") String analysisResult);

    /**
     * 清除分析结果（v1.9.0 新增，用于"重新分析"接口）
     *
     * @param id 文档 ID
     * @return 影响行数
     */
    @Update("UPDATE user_document SET analysis_result = NULL WHERE id = #{id}")
    int clearAnalysisResult(@Param("id") Long id);
}
