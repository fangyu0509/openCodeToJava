package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// 网页内容获取工具：发送 HTTP GET 请求获取 URL 内容
@Component
public class WebFetchTool implements Tool<Tool.Metadata> {

    // HTTP 客户端：带 15 秒连接超时，遵循重定向
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String id() { return "webfetch"; }

    @Override
    public String description() {
        return "Fetch the content of a web page or API endpoint. Returns the response body as text.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("url", "The URL to fetch", true)
            .string("format", "Response format: text (default), markdown, html")
            .build();
    }

    @Override
    // 执行请求：构造 GET 请求，设置超时和 User-Agent，获取响应体并截断后返回
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var url = args.get("url").asText();

        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "OpenCode/0.1.0")
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = ToolUtils.truncate(response.body());

            var sb = new StringBuilder();
            sb.append("URL: ").append(url).append("\n");
            sb.append("Status: ").append(response.statusCode()).append("\n\n");
            if (!body.isEmpty()) {
                sb.append(body);
            }

            return ExecuteResult.of("Fetched " + url, new Tool.Metadata() {}, sb.toString());
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to fetch " + url + ": " + e.getMessage());
        }
    }
}
