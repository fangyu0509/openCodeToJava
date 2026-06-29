package io.opencode.cli;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.mcp.McpService;
import io.opencode.core.session.Session;
import io.opencode.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenCodeCLITest {

    @Mock
    AgentLoop agentLoop;
    @Mock
    ToolRegistry toolRegistry;
    @Mock
    McpService mcpService;

    private OpenCodeConfig config;

    @BeforeEach
    void setUp() {
        config = OpenCodeConfig.defaults(Path.of("."));
    }

    @Test
    void constructor() {
        var cli = new OpenCodeCLI(config, toolRegistry, agentLoop, mcpService);
        assertNotNull(cli);
    }

    @Test
    void exitCommandTerminates() {
        when(toolRegistry.ids()).thenReturn(List.of());
        System.setIn(new ByteArrayInputStream("exit\n".getBytes()));
        var cli = new OpenCodeCLI(config, toolRegistry, agentLoop, mcpService);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) cli::run);
        System.setIn(System.in);
    }

    @Test
    void quitCommandTerminates() {
        when(toolRegistry.ids()).thenReturn(List.of());
        System.setIn(new ByteArrayInputStream("quit\n".getBytes()));
        var cli = new OpenCodeCLI(config, toolRegistry, agentLoop, mcpService);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) cli::run);
        System.setIn(System.in);
    }

    @Test
    void processesUserInput() throws Exception {
        when(toolRegistry.ids()).thenReturn(List.of("read"));
        when(agentLoop.process(any(Session.class), eq("hello"), any(AgentConfig.class), any()))
            .thenReturn(CompletableFuture.completedFuture(AgentResponse.text("hi", 1)));

        System.setIn(new ByteArrayInputStream("hello\nexit\n".getBytes()));
        var cli = new OpenCodeCLI(config, toolRegistry, agentLoop, mcpService);
        cli.run();
        System.setIn(System.in);

        verify(agentLoop, times(1)).process(any(), eq("hello"), any(), any());
    }
}
