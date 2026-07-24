package com.lawai.legalassistant.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawai.legalassistant.modules.auth.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审计日志 Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 分页查询审计日志（按时间倒序，v1.7.0 新增）
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @param action 操作类型过滤（可为 null）
     * @return 审计日志列表
     */
    @Select("<script>" +
            "SELECT id, user_id, action, ip, detail, created_at FROM audit_log " +
            "<where>" +
            "  <if test='action != null and action != \"\"'>" +
            "    AND action = #{action}" +
            "  </if>" +
            "</where>" +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<AuditLog> selectPage(@Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("action") String action);

    /**
     * 统计审计日志总数（v1.7.0 新增）
     *
     * @param action 操作类型过滤（可为 null）
     * @return 总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM audit_log " +
            "<where>" +
            "  <if test='action != null and action != \"\"'>" +
            "    AND action = #{action}" +
            "  </if>" +
            "</where>" +
            "</script>")
    long countByAction(@Param("action") String action);
}

