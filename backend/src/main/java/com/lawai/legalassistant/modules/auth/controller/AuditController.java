package com.lawai.legalassistant.modules.auth.controller;

import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.modules.auth.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志接口（v1.7.0 新增）
 * <p>
 * 仅 ADMIN 角色可访问，提供审计日志分页查看能力。
 */
@Tag(name = "审计日志", description = "审计日志查看（仅管理员）")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 分页查询审计日志
     *
     * @param page   页码（默认 1）
     * @param size   每页条数（默认 20，最大 100）
     * @param action 操作类型过滤（可选，如 LOGIN / LOGOUT / REGISTER）
     * @return 分页结果
     */
    @Operation(summary = "分页查询审计日志")
    @GetMapping("/logs")
    public Result<Map<String, Object>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action) {
        return Result.success(auditService.listAuditLogs(page, size, action));
    }
}
