package io.opencode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OpenCode Spring Boot 应用入口类
 * 负责启动整个 Spring 容器，加载所有自动配置和组件扫描
 */
@SpringBootApplication
public class OpenCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenCodeApplication.class, args);
    }
}
