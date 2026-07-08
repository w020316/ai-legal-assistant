package com.lawai.legalassistant.modules.auth.service;

import com.lawai.legalassistant.modules.auth.entity.AuditLog;
import com.lawai.legalassistant.modules.auth.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
}
