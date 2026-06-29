# openCodeToJava

[opencode](https://opencode.ai) AI 编程助手的 Java + Spring Boot 移植版，保留了原版的核心能力并加入 Java 生态集成。

## 特性

- **多 Provider 支持** — OpenAI、Anthropic、Ollama、OpenAI 兼容服务（DeepSeek、Groq、Together 等）
- **流式对话** — SSE 实时流式输出，支持多轮工具调用
- **13 个内置工具** — read、write、edit、glob、grep、shell、webfetch、websearch、question、task、lsp、skill、scout
- **Agent 系统** — build/plan/general/explore/architect/ask 多种 Agent，根据任务自动选择
- **LSP 集成** — 自动诊断、跳转定义、查找引用、悬停提示（支持 20+ 语言）
- **MCP 集成** — 支持 Model Context Protocol 服务发现与调用
- **技能系统** — 从 SKILL.md 或 .skill zip 文件中加载自定义技能
- **插件系统** — 支持 session start/message/tool 等事件钩子
- **会话管理** — 文件持久化（JSONL）、搜索、分享、分支（fork）
- **Web UI** — 暗色/亮色主题、Markdown 渲染、文件上传、@ 引用搜索
- **VSCode 扩展** — 侧边栏聊天面板、状态栏控制
- **自动格式化** — 支持 24 种文件格式（google-java-format、ruff、prettier 等）
- **自定义工具** — 通过 `.opencode/tools/*.json` 定义脚本工具
- **引用系统** — 通过 @alias 引用项目文件/目录
- **配置层级** — 全局 (`~/.config/opencode/`) + 项目配置合并
- **ACP 协议** — JSON-RPC over stdin/stdout 的 ACP 服务端
- **CLI 子命令** — serve、web、cli、session、acp、help

## 快速开始

### 前置条件

- JDK 24+
- Maven 3.9+
- 至少一个 AI 服务的 API Key

### 编译

```bash
mvn clean package -DskipTests
```

### 启动

```bash
# Web 服务 + UI（默认 http://localhost:8080）
java -jar target/opencode-*.jar

# 指定 API Key
OPENAI_API_KEY=sk-xxx java -jar target/opencode-*.jar
```

### Docker

```bash
docker-compose up --build
```

### VSCode 扩展

```bash
cd vscode-opencode
npm install
npm run compile
```

然后在 VSCode 中运行扩展，或打包：

```bash
npx vsce package
```

## 配置

支持 `opencode.json` 配置文件（项目根目录或 `~/.config/opencode/`）：

```json
{
  "provider": "openai",
  "model": "gpt-4o",
  "providers": {
    "openai": { "apiKey": "sk-xxx", "baseUrl": "https://api.openai.com/v1" },
    "deepseek": { "apiKey": "sk-xxx", "baseUrl": "https://api.deepseek.com/v1", "defaultModel": "deepseek-chat" }
  },
  "agents": {
    "build": { "model": "gpt-4o", "provider": "openai" }
  },
  "mcpServers": {
    "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "."] }
  },
  "references": {
    "docs": { "path": "./docs", "description": "项目文档", "patterns": ["*.md"] }
  },
  "compaction": { "threshold": 100000, "reserved": 5 }
}
```

环境变量：`OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`DEEPSEEK_API_KEY`、`OPENROUTER_API_KEY`、`GROQ_API_KEY`、`TOGETHER_API_KEY`、`OPENCODE_EXA_API_KEY`

## 测试

```bash
# 运行所有测试
mvn test

# 生成覆盖率报告
mvn verify
# 报告在 target/site/jacoco/index.html
```

当前 **383 个测试全部通过**。

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 24 |
| 框架 | Spring Boot 3.4.4 (WebFlux) |
| 构建 | Maven |
| 序列化 | Jackson |
| 测试 | JUnit 5 + Mockito |
| 覆盖率 | JaCoCo 0.8.13 |
| 前端 | 原生 HTML/CSS/JS |
| LSP/MCP | JSON-RPC 2.0 over stdio |

## 项目结构

```
src/main/java/io/opencode/
├── Main.java                 # CLI 入口（serve/web/cli/session/acp）
├── OpenCodeApplication.java  # Spring Boot 入口
├── cli/OpenCodeCLI.java      # 交互式 CLI
├── core/
│   ├── acp/                  # ACP 协议服务端
│   ├── agent/                # Agent 循环与配置
│   ├── config/               # 配置加载与合并
│   ├── event/                # 事件总线
│   ├── formatter/            # 自动格式化
│   ├── lsp/                  # 语言服务器协议
│   ├── mcp/                  # Model Context Protocol
│   ├── model/                # 数据模型
│   ├── permission/           # 权限检查
│   ├── plugin/               # 插件系统
│   ├── prompt/               # 提示词模板
│   ├── provider/             # AI Provider 抽象与实现
│   ├── session/              # 会话管理
│   ├── skill/                # 技能系统
│   ├── tool/                 # 工具系统（13 个工具）
│   └── util/                 # 工具类
└── server/                   # REST Controller、配置
```

## 截图

启动后访问 `http://localhost:8080` 打开 Web UI。

## 相关资源

- [opencode 官方文档](https://opencode.ai)
- [Spring Boot 参考文档](https://docs.spring.io/spring-boot/documentation.html)

## 许可证

[MIT](LICENSE)
