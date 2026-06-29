package io.opencode.core.provider;

import io.opencode.core.session.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    @Test
    void builderCreatesRequest() {
        var model = ModelRef.of("openai", "gpt-4");
        var req = ChatRequest.builder("system prompt", List.of(Message.userText("hi")), model)
            .temperature(0.5)
            .topP(0.9)
            .maxTokens(1000)
            .stream(true)
            .build();
        assertEquals("system prompt", req.systemPrompt());
        assertEquals(1, req.messages().size());
        assertEquals(model, req.model());
        assertEquals(0.5, req.temperature().get());
        assertEquals(0.9, req.topP().get());
        assertEquals(1000, req.maxTokens().get());
        assertTrue(req.stream());
    }

    @Test
    void builderDefaults() {
        var model = ModelRef.of("openai", "gpt-4");
        var req = ChatRequest.builder("sys", List.of(), model).build();
        assertTrue(req.temperature().isEmpty());
        assertTrue(req.topP().isEmpty());
        assertTrue(req.maxTokens().isEmpty());
        assertTrue(req.tools().isEmpty());
        assertTrue(req.stream());
    }

    @Test
    void toolDefinitionRecord() {
        var def = new ChatRequest.ToolDefinition("read", "read files", null);
        assertEquals("read", def.id());
        assertEquals("read files", def.description());
    }

    @Test
    void messagesAreCopied() {
        var msgs = new java.util.ArrayList<Message>();
        msgs.add(Message.userText("hi"));
        var model = ModelRef.of("openai", "gpt-4");
        var req = ChatRequest.builder("sys", msgs, model).build();
        msgs.add(Message.userText("extra"));
        assertEquals(1, req.messages().size());
    }

    @Test
    void toolsAreCopied() {
        var tools = new java.util.ArrayList<ChatRequest.ToolDefinition>();
        tools.add(new ChatRequest.ToolDefinition("read", "", null));
        var model = ModelRef.of("openai", "gpt-4");
        var req = ChatRequest.builder("sys", List.of(), model).tools(tools).build();
        tools.add(new ChatRequest.ToolDefinition("write", "", null));
        assertEquals(1, req.tools().size());
    }
}
