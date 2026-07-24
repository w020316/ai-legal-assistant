package com.lawai.legalassistant.ai;

import com.lawai.legalassistant.ai.client.AiRouter;
import com.lawai.legalassistant.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 联调测试接口（M1 阶段验证 Agnes AI 连通性）
 * <p>
 * 生产环境不放行，需 ADMIN 权限才能访问，防止公网白嫖 AI 算力。
 */
@Tag(name = "AI 联调测试", description = "Agnes AI 连通性验证接口（需管理员权限）")
@RestController
@RequestMapping("/api/v1/ai/test")
@PreAuthorize("hasRole('ADMIN')")
public class AiTestController {

    private static final Logger log = LoggerFactory.getLogger(AiTestController.class);
    private static final String SYSTEM_PROMPT = "你是一名专业的中国法律助手，请简明扼要地回答问题。";

    private final AiRouter aiRouter;

    public AiTestController(AiRouter aiRouter) {
        this.aiRouter = aiRouter;
    }

    @Operation(summary = "同步对话测试")
    @PostMapping("/chat")
    public Result<Map<String, Object>> testChat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "你好，请介绍一下你自己。");
        String answer = aiRouter.chat(SYSTEM_PROMPT, question);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", answer);
        return Result.success(result);
    }

    @Operation(summary = "向量化测试")
    @PostMapping("/embed")
    public Result<Map<String, Object>> testEmbed(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "民法典第一条规定。");
        float[] vector = aiRouter.embed(text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("dimension", vector.length);
        result.put("preview", Arrays.toString(Arrays.copyOf(vector, Math.min(5, vector.length))));
        return Result.success(result);
    }

    @Operation(summary = "连通性检查")
    @GetMapping("/ping")
    public Result<String> ping() {
        try {
            String reply = aiRouter.chat("回复 pong", "ping");
            return Result.success("AI 连通正常: " + reply);
        } catch (Exception e) {
            log.warn("AI 连通性检查失败", e);
            return Result.fail(2001, "AI 服务暂时不可用");
        }
    }
}
