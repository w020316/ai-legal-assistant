package com.lawai.legalassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 法律助手 API")
                        .description("面向法律从业者的智能问答与分析平台接口文档")
                        .version("v1.0")
                        .contact(new Contact().name("lawai").url("https://github.com/lawai/ai-legal-assistant"))
                        .license(new License().name("MIT")));
    }
}
