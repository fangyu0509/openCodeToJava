# 架构图与学习路线图

## 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        CLI 入口层                                │
│  Main.java (serve/web/cli/session/acp)                          │
│                         │                                        │
│                         ▼                                        │
│  ┌──────────────────────────────────────────────────────┐       │
│  │               Spring Boot 容器层                     │       │
│  │  OpenCodeApplication.java + AppConfiguration.java   │       │
│  │  (CORS, ConfigLoader, Bean 注册)                     │       │
│  └──────────┬──────────────────────┬───────────────────┘       │
│             │                      │                             │
│     ┌───────▼───────┐     ┌───────▼───────┐                    │
│     │  REST API 层  │     │  Web UI      │                    │
│     │ Controllers   │     │ index.html   │                    │
│     │ SessionCtrl   │     │ (SSE 流式)    │                    │
│     │ McpCtrl       │     └───────────────┘                    │
│     │ RefCtrl       │                                           │
│     └───────┬───────┘                                           │
│             │                                                    │
│             ▼                                                    │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                   核心业务层                            │     │
│  │                                                        │     │
│  │  ┌──────────────────────────────────────────────┐      │     │
│  │  │         DefaultAgentLoop (核心循环)          │      │     │
│  │  │  ┌─────┐  ┌──────┐  ┌───────┐  ┌───────┐   │      │     │
│  │  │  │命令 │  │工具  │  │引用   │  │插件   │   │      │     │
│  │  │  │处理 │→│执行  │→│解析   │→│事件   │   │      │     │
│  │  │  └─────┘  └──────┘  └───────┘  └───────┘   │      │     │
│  │  │  ┌─────┐  ┌──────┐  ┌───────┐              │      │     │
│  │  │  │压缩 │  │LSP   │  │格式化 │              │      │     │
│  │  │  │策略 │→│诊断  │→│器调用 │              │      │     │
│  │  │  └─────┘  └──────┘  └───────┘              │      │     │
│  │  └──────────────────────────────────────────────┘      │     │
│  │                                                        │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │     │
│  │  │ Provider │  │Tool      │  │  SessionManager  │     │     │
│  │  │ 抽象层   │  │注册表    │  │  (会话注册中心)   │     │     │
│  │  │ OpenAI   │  │13个内置  │  │  ┌─────────────┐ │     │     │
│  │  │ Anthropic│  │+ 自定义  │  │  │FileSession  │ │     │     │
│  │  │ Ollama   │  │+ MCP代理 │  │  │InMemory     │ │     │     │
│  │  │ 兼容服务 │  │+ Skill   │  │  │Shared       │ │     │     │
│  │  └──────────┘  └──────────┘  │  └─────────────┘ │     │     │
│  │                               └──────────────────┘     │     │
│  │                                                        │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │     │
│  │  │ 事件总线 │  │ 插件系统 │  │  技能系统        │     │     │
│  │  │ EventBus │  │PluginMgr │  │  SkillService    │     │     │
│  │  └──────────┘  └──────────┘  └──────────────────┘     │     │
│  └────────────────────────────────────────────────────────┘     │
│                         │                                        │
│                         ▼                                        │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                   集成服务层                            │     │
│  │  ┌─────────┐  ┌──────────┐  ┌────────┐  ┌─────────┐  │     │
│  │  │ LSP     │  │ MCP      │  │Formatter│  │ ACP     │  │     │
│  │  │Service  │  │Service   │  │Service  │  │Server   │  │     │
│  │  │ JsonRpc │  │McpClient │  │ (ruff,  │  │ (JSON-  │  │     │
│  │  │ LspServer│  │McpProxy  │  │prettier)│  │ RPC)   │  │     │
│  │  └─────────┘  └──────────┘  └────────┘  └─────────┘  │     │
│  └────────────────────────────────────────────────────────┘     │
│                         │                                        │
│                         ▼                                        │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                   数据层                                │     │
│  │  JSONL 文件 (sessions/) + JSON 配置 (opencode.json)     │     │
│  └────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
```

## 核心流程：一次对话的生命周期

```
用户输入 → POST /api/chat/{sessionId}
    │
    ▼
SessionController → AgentLoop.chatStream()
    │
    ▼
DefaultAgentLoop.run():
    ├─ 1. 处理内置命令 (/undo, /redo, /compact...)
    ├─ 2. 构建系统提示词 (含技能描述 + @引用解析)
    ├─ 3. 调用 Provider.chatStream() → SSE 流式输出
    ├─ 4. 检测工具调用 → 执行工具 (含权限检查)
    │     └─ 工具执行后: LSP 诊断 + 格式化器
    ├─ 5. 工具结果送回 LLM → 继续流式回复
    ├─ 6. 循环直到 LLM 返回完整文本
    ├─ 7. 压缩策略 (超过阈值则摘要历史)
    └─ 8. 持久化会话 → SSE 推送完成事件
```

## 缓存与上下文处理

| 机制 | 位置 | 作用 | 触发条件 |
|------|------|------|----------|
| 会话压缩 | `DefaultAgentLoop.compactSession()` | 超出阈值时用 LLM 总结替代全部历史 | >100k 字符 |
| 工具截断 | `ToolUtils.truncate()` | 防止工具输出撑爆上下文 | >200k 字符 |
| 引用嵌入 | `resolveReferences()` / `ReferenceService` | 将 @文件 内容直接读入用户消息 | 输入含 @xxx |
| 提示词构建 | `buildSystemPrompt()` | 组装工具定义 + 技能 + 引用 | 每次 LLM 调用 |
| 消息持久化 | `FileSession` JSONL | 磁盘存储，重启恢复 | 每次 append() |
| Token 统计 | `UsageTracker` | 记录每次调用的 token 数 | 每次 LLM 调用后 |

## 依赖关系图

```
Main.java
  └─ OpenCodeApplication (Spring Boot)
       └─ AppConfiguration
            ├─ ConfigLoader → OpenCodeConfig
            └─ ReferenceService

SessionController (REST API)
  ├─ SessionManager → FileSession / InMemorySession
  ├─ AgentLoop → DefaultAgentLoop
  │    ├─ ProviderRegistry → Provider (OpenAI / Anthropic / Ollama / 兼容)
  │    ├─ ToolRegistry → 13 个内置 Tool + CustomTool + MCP代理
  │    ├─ PromptLoader → AGENTS.md / prompts/*.txt
  │    ├─ PermissionChecker
  │    ├─ SnapshotManager (undo/redo)
  │    ├─ FormatterService
  │    ├─ LspService → LspServer → JsonRpc
  │    ├─ ReferenceService → ReferenceConfig
  │    ├─ SkillService → SkillLoader → SKILL.md
  │    ├─ PluginManager → Plugin
  │    └─ EventBus
  ├─ McpController → McpService → McpClient
  └─ ReferenceController

OpenCodeCLI (交互式命令行)
  └─ 同 AgentLoop 体系

AcpServer (ACP 协议)
  └─ JSON-RPC over stdin/stdout
```

---

## 学习路线图

### 第一阶段：理解整体脉络（1-2 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 1 | `src/main/java/io/opencode/Main.java` | 入口分发逻辑，subcommand 模式 |
| 2 | `src/main/java/io/opencode/OpenCodeApplication.java` | Spring Boot 启动 |
| 3 | `src/main/java/io/opencode/server/AppConfiguration.java` | 配置加载、CORS、Bean 注册 |
| 4 | `src/main/resources/application.yml` | 项目配置 |
| 5 | `pom.xml` | 依赖管理（WebFlux、Jackson 等） |

### 第二阶段：掌握配置系统（1 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 6 | `ConfigLoader.java` | JSON 解析 + 全局/项目配置合并 |
| 7 | `OpenCodeConfig.java` | 配置模型、静态实例、层级引用 |
| 8 | `opencode.json` | 配置文件结构 |

### 第三阶段：核心循环（2-3 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 9 | `AgentLoop.java` | 接口定义 |
| 10 | `DefaultAgentLoop.java` | **最核心文件**（880 行）— 完整理解整个循环流程 |
| 11 | `AgentConfig.java` / `AgentMode.java` / `BuiltinAgent.java` | Agent 配置与模式 |
| 12 | `AgentResponse.java` | 响应封装 |

重点关注 `DefaultAgentLoop` 中的：
- 命令处理 → 工具调用 → LLM 流式调用的编排
- 会话压缩策略
- 权限检查流程
- 插件事件触发点

### 第四阶段：Provider 抽象层（1-2 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 13 | `Provider.java` | 接口设计（chat/chatStream/defaultModel） |
| 14 | `ProviderRegistry.java` / `DefaultProviderRegistry.java` | 注册与查找 |
| 15 | `OpenAiProvider.java` | OpenAI 实现（HTTP + SSE 解析） |
| 16 | `AnthropicProvider.java` | Anthropic 实现（消息格式差异） |
| 17 | `OllamaProvider.java` | Ollama 实现（本地模型） |
| 18 | `OpenAICompatibleProvider.java` | 兼容服务（DeepSeek/Groq 等） |
| 19 | `ProviderConfigurer.java` | 自动发现与动态注册 |
| 20 | `ChatRequest.java` / `ChatResponse.java` / `ChatChunk.java` | 请求/响应模型 |

### 第五阶段：工具系统（2 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 21 | `Tool.java` | 工具接口设计 |
| 22 | `ToolRegistry.java` / `DefaultToolRegistry.java` | 注册表 + Agent 过滤 |
| 23 | `ToolContext.java` | 上下文传递（会话、事件总线等） |
| 24 | `ReadTool.java` / `WriteTool.java` / `EditTool.java` | 文件操作工具 |
| 25 | `ShellTool.java` | 命令执行 + 权限检查 |
| 26 | `WebSearchTool.java` | Web 搜索（DuckDuckGo + Exa） |
| 27 | `QuestionTool.java` | 异步提问（事件总线） |
| 28 | `TaskTool.java` | 子 Agent 委托 |
| 29 | `CustomToolLoader.java` | 自定义 JSON 工具加载 |
| 30 | `ScoutTool.java` | 调研 Agent |

### 第六阶段：会话管理（1-2 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 31 | `Session.java` | 接口定义 |
| 32 | `InMemorySession.java` | 内存实现 |
| 33 | `FileSession.java` | JSONL 文件持久化 |
| 34 | `SessionManager.java` | 注册中心 + 生命周期 |
| 35 | `Message.java` | 消息类型体系（Text/ToolCall/ToolResult/File） |
| 36 | `SnapshotManager.java` | 快照（undo/redo） |
| 37 | `SharedSessionService.java` | 会话分享 |

### 第七阶段：REST API + Web UI（2 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 38 | `SessionController.java` | 全部 REST 端点（CRUD + SSE 流式 + 搜索 + 分支） |
| 39 | `McpController.java` | MCP 管理 API |
| 40 | `ReferenceController.java` | 引用 API |
| 41 | `index.html` | 前端实现（SSE 流式渲染、主题切换、文件上传） |

### 第八阶段：集成服务（2-3 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 42 | `JsonRpc.java` | JSON-RPC 2.0 协议实现 |
| 43 | `LspServer.java` | LSP 进程生命周期 |
| 44 | `LspService.java` | 语言服务器管理 |
| 45 | `LspTool.java` | LSP 工具（定义/引用/悬停/诊断） |
| 46 | `McpClient.java` | MCP 客户端 |
| 47 | `McpService.java` | MCP 服务管理 |
| 48 | `FormatterService.java` | 自动格式化器 |
| 49 | `AcpServer.java` | ACP 服务端 |

### 第九阶段：扩展系统（1 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 50 | `Plugin.java` / `PluginManager.java` | 插件接口 + 事件钩子 |
| 51 | `Skill.java` / `SkillLoader.java` / `SkillService.java` | 技能加载 |
| 52 | `SkillTool.java` | 技能执行工具 |
| 53 | `ReferenceService.java` / `ReferenceConfig.java` | @引用解析 |
| 54 | `EventBus.java` / `SimpleEventBus.java` | 事件总线实现 |

### 第十阶段：CLI + DevOps（1 天）

| 步骤 | 文件 | 学什么 |
|------|------|--------|
| 55 | `OpenCodeCLI.java` | 交互式命令行实现（ANSI 颜色、流式显示） |
| 56 | `Dockerfile` / `docker-compose.yml` / `Makefile` | 容器化与构建 |
| 57 | `ci.yml` | GitHub Actions |

### 第十一阶段：测试学习（贯穿始终）

| 类别 | 示例文件 | 学什么 |
|------|----------|--------|
| 单元测试 | `DefaultAgentLoopTest.java` | Mock Provider/ToolRegistry 测试核心循环 |
| 集成测试 | `DefaultAgentLoopIntegrationTest.java` | 真实组件装配 |
| 工具测试 | `ReadToolTest.java` | 文件操作边界条件 |
| Provider 测试 | `OpenAiProviderTest.java` | HTTP 模拟 |
| 配置测试 | `ConfigLoaderTest.java` | JSON 加载/合并 |

## 学习策略建议

1. **自顶向下** — 从 Main → Controller → AgentLoop → Provider/Tool，理解数据流
2. **单步调试** — 启动应用 `mvn spring-boot:run`，打断点跟踪一次完整对话
3. **改代码测试** — 每个模块都有单元测试，改了代码跑 `mvn test` 验证
4. **关注核心** — `DefaultAgentLoop.java` 是心脏，读懂了它就读懂了 70%
5. **不要孤立看文件** — 理解接口 → 实现 → 测试的模式，每个工具/Provider 都遵循相同契约
