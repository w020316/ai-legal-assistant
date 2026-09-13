package com.lawai.legalassistant.modules.auth.service;

import com.lawai.legalassistant.modules.auth.entity.AuditLog;
import com.lawai.legalassistant.modules.auth.mapper.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuditService} 单元测试（v1.18 新增，补覆盖率洼地）。
 * <p>
 * 覆盖异步审计写入（含失败吞掉不抛出）、分页参数钳制与分页结果组装。
 */
@DisplayName("AuditService 审计服务")
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Test
    @DisplayName("record：写入完整审计字段")
    void recordWritesFields() {
        when(auditLogMapper.insert(any(AuditLog.class))).thenReturn(1);

        new AuditService(auditLogMapper).record(42L, "LOGIN", "1.2.3.4", "{\"username\":\"a\"}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getAction()).isEqualTo("LOGIN");
        assertThat(captor.getValue().getIp()).isEqualTo("1.2.3.4");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("record：写库失败不抛出（审计不阻塞业务）")
    void recordFailureSwallowed() {
        when(auditLogMapper.insert(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        org.assertj.core.api.Assertions.assertThatCode(
                        () -> new AuditService(auditLogMapper).record(1L, "LOGOUT", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("分页：非法 page/size 钳制到默认值")
    void listClampsParams() {
        when(auditLogMapper.selectPage(anyInt(), anyInt(), anyString())).thenReturn(List.of());
        when(auditLogMapper.countByAction(anyString())).thenReturn(25L);

        Map<String, Object> result = new AuditService(auditLogMapper).listAuditLogs(-1, 9999, "LOGIN");

        verify(auditLogMapper).selectPage(eq(0), eq(20), eq("LOGIN"));
        assertThat(result.get("total")).isEqualTo(25L);
        assertThat(result.get("pages")).isEqualTo(2L);
        assertThat(result.get("size")).isEqualTo(20);
    }
}
