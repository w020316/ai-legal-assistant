package com.lawai.legalassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 法律助手后端启动类
 *
 * @author lawai
 */
@SpringBootApplication
@MapperScan("com.lawai.legalassistant.modules.**.mapper")
public class LawAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawAiApplication.class, args);
    }
}
