package io.opencode.server;

import io.opencode.core.config.ConfigLoader;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.config.ReferenceConfig;
import io.opencode.core.tool.util.ReferenceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.nio.file.Path;
import java.util.List;

// Spring Boot 应用配置类：加载配置、注册引用、配置 CORS
@Configuration
public class AppConfiguration {
    private final ConfigLoader configLoader;
    private final ReferenceService referenceService;

    public AppConfiguration(ConfigLoader configLoader, ReferenceService referenceService) {
        this.configLoader = configLoader;
        this.referenceService = referenceService;
    }

    // 加载 opencode 配置，设置为全局单例，并配置引用
    @Bean
    public OpenCodeConfig openCodeConfig() {
        var workspaceDir = Path.of(System.getProperty("user.dir"));
        var config = configLoader.load(workspaceDir);
        OpenCodeConfig.setInstance(config);
        configureReferences(config);
        return config;
    }

    // 将配置中的引用列表注入到 ReferenceService
    private void configureReferences(OpenCodeConfig config) {
        var refMap = new java.util.LinkedHashMap<String, ReferenceConfig>();
        for (var ref : config.references()) {
            refMap.put(ref.name(), ref);
        }
        referenceService.configure(refMap);
    }

    // 配置 CORS 过滤器，允许所有来源访问 /api/** 路径
    @Bean
    public CorsWebFilter corsWebFilter() {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }
}
