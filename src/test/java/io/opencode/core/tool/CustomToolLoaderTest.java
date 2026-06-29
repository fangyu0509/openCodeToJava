package io.opencode.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CustomToolLoaderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsEmptyListForNoToolsDir(@TempDir Path tempDir) {
        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertTrue(tools.isEmpty());
    }

    @Test
    void loadsToolFromJsonFile(@TempDir Path tempDir) throws Exception {
        var toolsDir = tempDir.resolve(".opencode").resolve("tools");
        Files.createDirectories(toolsDir);
        var toolFile = toolsDir.resolve("greet.json");
        var json = mapper.createObjectNode();
        json.put("name", "greet");
        json.put("description", "Says hello");
        var command = json.putArray("command");
        command.add("echo");
        command.add("hello");
        mapper.writeValue(toolFile.toFile(), json);

        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertEquals(1, tools.size());
        var tool = tools.get(0);
        assertEquals("greet", tool.id());
        assertEquals("Says hello", tool.description());
        assertNotNull(tool.parameters());
    }

    @Test
    void skipsToolWithMissingCommand(@TempDir Path tempDir) throws Exception {
        var toolsDir = tempDir.resolve(".opencode").resolve("tools");
        Files.createDirectories(toolsDir);
        var toolFile = toolsDir.resolve("bad.json");
        var json = mapper.createObjectNode();
        json.put("name", "bad");
        mapper.writeValue(toolFile.toFile(), json);

        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertTrue(tools.isEmpty());
    }

    @Test
    void loadsToolWithArgs(@TempDir Path tempDir) throws Exception {
        var toolsDir = tempDir.resolve(".opencode").resolve("tools");
        Files.createDirectories(toolsDir);
        var toolFile = toolsDir.resolve("repeat.json");
        var json = mapper.createObjectNode();
        json.put("name", "repeat");
        var command = json.putArray("command");
        command.add("echo");
        var args = json.putObject("args");
        args.put("text", "string");
        args.put("times", "number");
        mapper.writeValue(toolFile.toFile(), json);

        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertEquals(1, tools.size());
        assertEquals("repeat", tools.get(0).id());
    }

    @Test
    void loadsToolWithIdFallback(@TempDir Path tempDir) throws Exception {
        var toolsDir = tempDir.resolve(".opencode").resolve("tools");
        Files.createDirectories(toolsDir);
        var toolFile = toolsDir.resolve("my-tool.json");
        var json = mapper.createObjectNode();
        json.put("id", "my-tool");
        var command = json.putArray("command");
        command.add("ls");
        mapper.writeValue(toolFile.toFile(), json);

        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertEquals(1, tools.size());
        assertEquals("my-tool", tools.get(0).id());
    }

    @Test
    void loadsMultipleTools(@TempDir Path tempDir) throws Exception {
        var toolsDir = tempDir.resolve(".opencode").resolve("tools");
        Files.createDirectories(toolsDir);

        var t1 = toolsDir.resolve("a.json");
        var j1 = mapper.createObjectNode();
        j1.put("name", "tool-a");
        var c1 = j1.putArray("command"); c1.add("echo"); c1.add("a");
        mapper.writeValue(t1.toFile(), j1);

        var t2 = toolsDir.resolve("b.json");
        var j2 = mapper.createObjectNode();
        j2.put("name", "tool-b");
        var c2 = j2.putArray("command"); c2.add("echo"); c2.add("b");
        mapper.writeValue(t2.toFile(), j2);

        var loader = new CustomToolLoader(tempDir);
        var tools = loader.loadTools();
        assertEquals(2, tools.size());
    }
}
