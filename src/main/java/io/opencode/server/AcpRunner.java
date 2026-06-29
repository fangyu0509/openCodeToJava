package io.opencode.server;

import io.opencode.core.acp.AcpServer;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.impl.DefaultAgentLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// ACP 模式启动器，在 acp profile 下通过 stdin/stdout 启动代理通信服务
@Component
@Profile("acp")
public class AcpRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AcpRunner.class);

    private final DefaultAgentLoop agentLoop;

    public AcpRunner(DefaultAgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    @Override
    public void run(String... args) {
        log.info("Starting ACP server on stdin/stdout");
        // 使用默认的代理配置（OpenAI GPT-4，最多 20 步）
        var config = AgentConfig.builder("general")
            .model(io.opencode.core.provider.ModelRef.of("openai", "gpt-4"))
            .maxSteps(20)
            .build();
        var server = new AcpServer(agentLoop, config);
        server.start();
    }
}
