package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TaskToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentLoopStub agentLoop = new AgentLoopStub();
    private final TaskTool tool = new TaskTool(agentLoop);

    @Test
    void testId() {
        assertEquals("task", tool.id());
    }

    @Test
    void testDescription() {
        assertTrue(tool.description().contains("sub-agent"));
    }

    @Test
    void testParameters() {
        var schema = tool.parameters().schema().toString();
        assertTrue(schema.contains("agent"));
        assertTrue(schema.contains("prompt"));
    }

    @Test
    void testExecuteCatchesError() {
        agentLoop.throwOnProcess = true;
        var args = mapper.createObjectNode()
            .put("agent", "general")
            .put("prompt", "do something");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("failed"));
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }

    private static class AgentLoopStub implements AgentLoop {
        boolean throwOnProcess = false;

        @Override
        public CompletableFuture<AgentResponse> process(
                io.opencode.core.session.Session session,
                String userInput,
                io.opencode.core.agent.AgentConfig config) {
            if (throwOnProcess) {
                return CompletableFuture.failedFuture(new RuntimeException("stub error"));
            }
            return CompletableFuture.completedFuture(
                new AgentResponse(AgentResponse.Type.TEXT, Optional.of("stub result"), Optional.empty(), 1)
            );
        }

        @Override
        public CompletableFuture<AgentResponse> process(
                io.opencode.core.session.Session session,
                String userInput,
                io.opencode.core.agent.AgentConfig config,
                java.util.function.Consumer<String> onEvent) {
            return process(session, userInput, config);
        }
    }
}
