package com.lawai.legalassistant.modules.health;

import com.lawai.legalassistant.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口
 */
@Tag(name = "健康检查", description = "服务健康与连通性探测")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("service", "ai-legal-assistant");
        info.put("timestamp", Instant.now());
        // DB/Redis/Agnes 连通性检查将在 M1 后续补充
        return Result.success(info);
    }
}
