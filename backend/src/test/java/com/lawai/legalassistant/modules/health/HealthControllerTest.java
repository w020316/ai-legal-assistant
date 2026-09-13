package com.lawai.legalassistant.modules.health;

import com.lawai.legalassistant.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link HealthController} 单元测试（v1.18 新增，补覆盖率洼地）。
 * <p>
 * 锁定 P2#16 修复后的探测语义：DB UP + Redis UP → 200/UP；
 * Redis 挂 → 200/DEGRADED（fail-open 降级）；DB 挂 → 503/DOWN。
 */
@DisplayName("HealthController 健康探测")
@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Result<Map<String, Object>>> resp) {
        return resp.getBody().data();
    }

    @Test
    @DisplayName("DB UP + Redis UP → 200/UP")
    void allUp() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        var resp = new HealthController(jdbcTemplate, redisTemplate).health();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(resp).get("status")).isEqualTo("UP");
        assertThat(((Map<String, Object>) body(resp).get("components")).get("database")).isEqualTo("UP");
    }

    @Test
    @DisplayName("Redis 挂 → 200/DEGRADED（fail-open 降级非故障）")
    void redisDownDegraded() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("redis down"));

        var resp = new HealthController(jdbcTemplate, redisTemplate).health();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(resp).get("status")).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("DB 挂 → 503/DOWN")
    void dbDown() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("db down"));

        var resp = new HealthController(jdbcTemplate, redisTemplate).health();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(resp).get("status")).isEqualTo("DOWN");
    }
}
