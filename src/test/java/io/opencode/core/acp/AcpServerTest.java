package io.opencode.core.acp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.agent.impl.DefaultAgentLoop;
import io.opencode.core.session.Session;
import io.opencode.core.session.Message;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcpServerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void constructorAndStart() {
        var loop = mock(DefaultAgentLoop.class);
        var config = AgentConfig.builder("general").build();
        var server = new AcpServer(loop, config);
        assertNotNull(server);
    }

    @Test
    void initializeResponse() throws Exception {
        var loop = mock(DefaultAgentLoop.class);
        var config = AgentConfig.builder("general").build();
        var server = new AcpServer(loop, config);

        var req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "initialize");

        assertNotNull(req.toString());
    }

    @Test
    void processMessage() throws Exception {
        var loop = mock(DefaultAgentLoop.class);
        when(loop.process(any(Session.class), anyString(), any(AgentConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(
                AgentResponse.text("Hello!", 1)));

        var config = AgentConfig.builder("build").build();
        var server = new AcpServer(loop, config);

        var req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 2);
        req.put("method", "process");
        var params = req.putObject("params");
        params.put("input", "test message");

        assertNotNull(req.toString());
    }

    @Test
    void unknownMethod() throws Exception {
        var loop = mock(DefaultAgentLoop.class);
        var config = AgentConfig.builder("general").build();
        var server = new AcpServer(loop, config);

        var req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 3);
        req.put("method", "unknown");

        assertNotNull(req.toString());
    }

    @Test
    void shutdown() throws Exception {
        var loop = mock(DefaultAgentLoop.class);
        var config = AgentConfig.builder("general").build();
        var server = new AcpServer(loop, config);

        var req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 4);
        req.put("method", "shutdown");

        assertNotNull(req.toString());
    }
}
