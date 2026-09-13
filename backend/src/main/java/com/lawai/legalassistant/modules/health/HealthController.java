package com.lawai.legalassistant.modules.health;

import com.lawai.legalassistant.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口
 * <p>
 * v1.18（审查 P2#16）：此前恒返回硬编码 "UP"（注释自认 DB/Redis 连通性未接），
 * DB 宕机/AI 配额耗尽时监控页仍全绿，健康检查失去正确性。现接入真实探测：
 * DB 不可用 → 503 + DOWN；Redis 不可用 → DEGRADED（黑名单 fail-open 设计下功能仍可用）。
 * AI 依赖不做主动探测（消耗配额且免费档高频探测会被限流），由降级链自身兜底。
 */
@Tag(name = "健康检查", description = "服务健康与连通性探测")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Operation(summary = "健康检查")
    @GetMapping
    public ResponseEntity<Result<Map<String, Object>>> health() {
        boolean dbUp = checkDatabase();
        boolean redisUp = checkRedis();

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", dbUp ? "UP" : "DOWN");
        components.put("redis", redisUp ? "UP" : "DEGRADED");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "ai-legal-assistant");
        info.put("timestamp", Instant.now());
        String status = dbUp ? (redisUp ? "UP" : "DEGRADED") : "DOWN";
        info.put("status", status);
        info.put("components", components);

        if (!dbUp) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.success(info));
        }
        return ResponseEntity.ok(Result.success(info));
    }

    /** DB 连通性：SELECT 1 探测 */
    private boolean checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Redis 连通性：PING 探测 */
    private boolean checkRedis() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }
}
