package io.opencode.core.lsp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void sendsRequest() throws Exception {
        var out = new ByteArrayOutputStream();
        var rpc = new JsonRpc(new ByteArrayInputStream(new byte[0]), out);

        var params = MAPPER.createObjectNode();
        params.put("test", true);
        rpc.sendRequest("test/method", params);

        Thread.sleep(200);
        var sent = out.toString();
        assertTrue(sent.contains("test/method"));
        assertTrue(sent.contains("2.0"));
        assertTrue(sent.contains("\"id\":1"));

        rpc.close();
    }

    @Test
    void sendsNotification() throws Exception {
        var out = new ByteArrayOutputStream();
        var rpc = new JsonRpc(new ByteArrayInputStream(new byte[0]), out);

        rpc.sendNotification("test/notify", MAPPER.createObjectNode());

        Thread.sleep(100);
        var sent = out.toString();
        assertTrue(sent.contains("test/notify"));
        assertTrue(sent.contains("2.0"));
        assertFalse(sent.contains("\"id\""));

        rpc.close();
    }

    @Test
    void closeIsSafe() {
        var rpc = new JsonRpc(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        rpc.close();
        rpc.close();
    }
}
