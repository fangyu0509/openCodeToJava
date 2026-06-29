package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 网络搜索工具：通过配置的搜索引擎（API、Exa 或 DuckDuckGo）搜索网络信息
@Component
public class WebSearchTool implements Tool<Tool.Metadata> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // HTTP 客户端：带 15 秒连接超时，遵循重定向
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String id() { return "websearch"; }

    @Override
    public String description() {
        return "Search the web for information. Returns search results from the configured search engine.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("query", "The search query", true)
            .string("type", "Search type: auto (default), fast, deep")
            .number("numResults", "Number of results to return (default 5)")
            .build();
    }

    @Override
    // 根据环境变量配置选择合适的搜索引擎（API > Exa > DuckDuckGo）
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var query = args.get("query").asText();
        var numResults = args.has("numResults") ? args.get("numResults").asInt(5) : 5;

        var apiKey = System.getenv("OPENCODE_SEARCH_API_KEY");
        var endpoint = System.getenv("OPENCODE_SEARCH_ENDPOINT");
        var exaKey = System.getenv("OPENCODE_EXA_API_KEY");

        // 首选自定义搜索 API
        if (apiKey != null && endpoint != null) {
            return searchWithApi(query, apiKey, endpoint, numResults);
        }

        // 其次 Exa 搜索
        if (exaKey != null) {
            return searchWithExa(query, exaKey, numResults);
        }

        // 默认使用 DuckDuckGo 搜索
        return searchWithDuckDuckGoApi(query, numResults);
    }

    // 通过自定义 API 搜索，POST JSON 请求体
    private ExecuteResult<Tool.Metadata> searchWithApi(String query, String apiKey, String endpoint, int numResults) {
        try {
            var body = MAPPER.writeValueAsString(java.util.Map.of("query", query, "numResults", numResults));
            var request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ExecuteResult.of("Search: " + query, Tool.Metadata.EMPTY,
                "Search results for: " + query + "\n\n" + ToolUtils.truncate(response.body()));
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Search failed: " + e.getMessage());
        }
    }

    // 通过 DuckDuckGo 即时答案 API 搜索，解析返回的 JSON 结果
    private ExecuteResult<Tool.Metadata> searchWithDuckDuckGoApi(String query, int numResults) {
        try {
            var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            var url = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1";

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "OpenCode/0.1.0")
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var root = MAPPER.readTree(response.body());

            var sb = new StringBuilder();
            sb.append("Query: ").append(query).append("\n");

            // 提取摘要（AbstractText）
            var abstractText = root.has("AbstractText") ? root.get("AbstractText").asText() : "";
            if (!abstractText.isBlank()) {
                sb.append("\nSummary: ").append(abstractText).append("\n");
            }

            var abstractUrl = root.has("AbstractURL") ? root.get("AbstractURL").asText() : "";
            if (!abstractUrl.isBlank()) {
                sb.append("Source: ").append(abstractUrl).append("\n");
            }

            // 提取 Results 数组
            var results = root.get("Results");
            if (results != null && results.isArray()) {
                var count = 0;
                for (var r : results) {
                    if (count >= numResults) break;
                    var text = r.has("Text") ? r.get("Text").asText() : "";
                    var firstUrl = r.has("FirstURL") ? r.get("FirstURL").asText() : "";
                    if (!text.isBlank()) {
                        sb.append("\n").append(++count).append(". ").append(text).append("\n");
                        if (!firstUrl.isBlank()) sb.append("   ").append(firstUrl).append("\n");
                    }
                }
            }

            // 提取 RelatedTopics（含嵌套 Topics）
            var related = root.get("RelatedTopics");
            if (related != null && related.isArray()) {
                var count = 0;
                for (var r : related) {
                    if (count >= numResults) break;
                    if (r.has("Text")) {
                        sb.append("\n").append(++count).append(". ").append(r.get("Text").asText()).append("\n");
                        if (r.has("FirstURL")) sb.append("   ").append(r.get("FirstURL").asText()).append("\n");
                    }
                    // 处理嵌套的 Topics
                    if (r.has("Topics") && r.get("Topics").isArray()) {
                        for (var t : r.get("Topics")) {
                            if (count >= numResults) break;
                            if (t.has("Text")) {
                                sb.append("\n").append(++count).append(". ").append(t.get("Text").asText()).append("\n");
                                if (t.has("FirstURL")) sb.append("   ").append(t.get("FirstURL").asText()).append("\n");
                            }
                        }
                    }
                }
            }

            var result = sb.toString().strip();
            if (result.equals("Query: " + query)) {
                result = "No results found for: " + query;
            }

            return ExecuteResult.of("Search: " + query, Tool.Metadata.EMPTY,
                "Search results for: " + query + "\n\n" + result);
        } catch (Exception e) {
            // API 失败时回退到 HTML 解析方式
            return searchWithDuckDuckGoHtml(query, numResults);
        }
    }

    // 通过 Exa API 搜索
    private ExecuteResult<Tool.Metadata> searchWithExa(String query, String apiKey, int numResults) {
        try {
            var body = MAPPER.writeValueAsString(java.util.Map.of(
                "query", query,
                "numResults", numResults,
                "type", "auto"
            ));
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.exa.ai/search"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var root = MAPPER.readTree(response.body());
            var sb = new StringBuilder();
            sb.append("Query: ").append(query).append("\n");
            var results = root.get("results");
            if (results != null && results.isArray()) {
                var count = 0;
                for (var r : results) {
                    if (count >= numResults) break;
                    var title = r.has("title") ? r.get("title").asText() : "";
                    var url = r.has("url") ? r.get("url").asText() : "";
                    var snippet = r.has("snippet") ? r.get("snippet").asText() : "";
                    sb.append("\n").append(++count).append(". ").append(title).append("\n");
                    sb.append("   ").append(url).append("\n");
                    if (!snippet.isBlank()) sb.append("   ").append(snippet).append("\n");
                }
            }
            return ExecuteResult.of("Search: " + query, Tool.Metadata.EMPTY,
                sb.toString().strip());
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Exa search failed: " + e.getMessage());
        }
    }

    // 回退方式：通过解析 DuckDuckGo HTML 搜索结果页
    private ExecuteResult<Tool.Metadata> searchWithDuckDuckGoHtml(String query, int numResults) {
        try {
            var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            var url = "https://html.duckduckgo.com/html/?q=" + encoded;
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "OpenCode/0.1.0")
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = ToolUtils.truncate(extractText(response.body()));
            return ExecuteResult.of("Search: " + query, Tool.Metadata.EMPTY,
                "Search results for: " + query + "\n\n" + body);
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Search failed: " + e.getMessage());
        }
    }

    // 简易 HTML 转纯文本：移除所有 HTML 标签，合并空白
    private String extractText(String html) {
        return html.replaceAll("<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .strip();
    }
}
