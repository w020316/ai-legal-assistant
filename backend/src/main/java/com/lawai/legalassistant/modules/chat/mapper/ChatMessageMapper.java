package com.lawai.legalassistant.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawai.legalassistant.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 消息 Mapper
 * <p>
 * citations 为 JSONB，插入时使用 CAST(? AS jsonb) 显式转换，
 * 避免依赖 JDBC URL 的 stringtype=unspecified 配置。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 插入消息（citations 以 jsonb 写入，null 安全）
     *
     * @param message 消息实体
     * @return 影响行数
     */
    @Insert("INSERT INTO chat_message(session_id, role, content, citations, tokens, created_at) " +
            "VALUES(#{sessionId}, #{role}, #{content}, CAST(#{citations} AS jsonb), #{tokens}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(ChatMessage message);

    /**
     * 查询某会话最近的消息（按创建时间倒序）
     *
     * @param sessionId 会话 ID
     * @param limit     取多少条
     * @return 消息列表（倒序）
     */
    @Select("SELECT id, session_id, role, content, " +
            "CAST(citations AS TEXT) AS citations, tokens, created_at " +
            "FROM chat_message WHERE session_id = #{sessionId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> selectRecent(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /**
     * 批量查询多个会话的最后一条消息（v1.7.0 新增，解决 N+1 查询）
     * <p>
     * 使用 PostgreSQL 的 DISTINCT ON 语法，每个 session_id 仅返回最新一条消息，
     * 将原本的 1+N 次查询降为 1+1 次。
     *
     * @param sessionIds 会话 ID 集合
     * @return 每个会话的最后一条消息列表
     */
    @Select("<script>" +
            "SELECT id, session_id, role, content, " +
            "CAST(citations AS TEXT) AS citations, tokens, created_at " +
            "FROM chat_message " +
            "WHERE session_id IN " +
            "<foreach item='id' collection='sessionIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "AND id IN (" +
            "  SELECT DISTINCT ON (session_id) id FROM chat_message " +
            "  WHERE session_id IN " +
            "  <foreach item='id' collection='sessionIds' open='(' separator=',' close=')'>" +
            "  #{id}" +
            "  </foreach> " +
            "  ORDER BY session_id, created_at DESC" +
            ") " +
            "ORDER BY session_id" +
            "</script>")
    List<ChatMessage> selectLastMessagesBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}

