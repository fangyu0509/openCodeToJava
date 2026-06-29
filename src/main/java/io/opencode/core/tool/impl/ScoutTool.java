package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.JsonSchema;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// 调研工具：通过 DuckDuckGo HTML 搜索进行网络调研，用于查询库、API 或依赖信息
@Component
public class ScoutTool implements Tool<Tool.Metadata> {
    // 静态初始化参数 Schema（手动构建，不依赖 ToolUtils）
    private static final JsonSchema PARAMS;

    static {
        var factory = JsonNodeFactory.instance;
        var props = factory.objectNode();
        props.set("query", factory.objectNode()
            .put("type", "string")
            .put("description", "Research query"));
        props.set("maxResults", factory.objectNode()
            .put("type", "number")
            .put("description", "Max search results (default 5)"));
        var schema = factory.objectNode();
        schema.put("type", "object");
        schema.set("properties", props);
        PARAMS = JsonSchema.fromNode(schema);
    }

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public String id() { return "scout"; }

    @Override
    public String description() { return "Research a topic via web search. Use when you need external information about libraries, APIs, or dependencies."; }

    @Override
    public JsonSchema parameters() { return PARAMS; }

    @Override
    // 执行调研：发送 DuckDuckGo HTML 搜索请求，解析 result__snippet 提取片段
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var query = args.has("query") ? args.get("query").asText("") : "";
        if (query.isBlank()) return ExecuteResult.of("scout", Tool.Metadata.EMPTY, "Error: 'query' is required");
        var maxResults = args.has("maxResults") ? args.get("maxResults").asInt(5) : 5;
        try {
            var searchUrl = "https://html.duckduckgo.com/html/?q=" +
                java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            var req = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            var body = resp.body();

            // 解析 HTML 中的 result__snippet 片段
            var results = new java.util.ArrayList<String>();
            var snippetStart = body.indexOf("class=\"result__snippet\"");
            for (int i = 0; i < maxResults && snippetStart >= 0; i++) {
                var contentStart = body.indexOf(">", snippetStart) + 1;
                var contentEnd = body.indexOf("</", contentStart);
                if (contentStart > 0 && contentEnd > contentStart) {
                    var snippet = body.substring(contentStart, contentEnd)
                        .replaceAll("<[^>]+>", "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#x27;", "'")
                        .replace("&quot;", "\"")
                        .trim();
                    results.add(snippet);
                }
                snippetStart = body.indexOf("class=\"result__snippet\"", contentEnd);
            }

            var sb = new StringBuilder();
            sb.append("## Research: ").append(query).append("\n\n");
            for (int i = 0; i < results.size(); i++) {
                sb.append(i + 1).append(". ").append(results.get(i)).append("\n\n");
            }
            if (results.isEmpty()) sb.append("No results found.\n");

            return ExecuteResult.of("scout", Tool.Metadata.EMPTY, sb.toString());
        } catch (Exception e) {
            return ExecuteResult.of("scout", Tool.Metadata.EMPTY, "Research error: " + e.getMessage());
        }
    }
}
