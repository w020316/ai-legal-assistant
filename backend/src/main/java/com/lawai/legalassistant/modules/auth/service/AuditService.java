package com.lawai.legalassistant.modules.auth.service;

import com.lawai.legalassistant.modules.auth.entity.AuditLog;
import com.lawai.legalassistant.modules.auth.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 异步记录审计日志
     */
    @Async
    public void record(Long userId, String action, String ip, String detail) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setIp(ip);
            auditLog.setDetail(detail);
            auditLog.setCreatedAt(Instant.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    /**
     * 分页查询审计日志（v1.7.0 新增，供 ADMIN 后台查看）
     *
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param action 操作类型过滤（可为 null）
     * @return 分页结果，包含 records / total / page / size
     */
    public Map<String, Object> listAuditLogs(int page, int size, String action) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        List<AuditLog> records = auditLogMapper.selectPage(offset, size, action);
        long total = auditLogMapper.countByAction(action);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("pages", (total + size - 1) / size);
        return result;
    }
}

