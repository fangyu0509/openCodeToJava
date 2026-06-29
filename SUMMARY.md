# OpenCode (Java Port) - Project Summary

## Goal
- Port opencode (TypeScript AI coding assistant) to Java with Spring Boot, including provider abstraction, tool system, agent loop, plugin system, web UI, VSCode extension, LSP/MCP integration, skill system, image handling, interactive CLI, session sharing, conversation search, session forking, config hierarchy, auto-formatters, custom tools, references system, scout agent, ACP server, CLI subcommands

## Constraints & Preferences
- Java 24, Spring Boot 3.4.4 (WebFlux reactive), Jackson, Maven
- SSE streaming for real-time frontend updates; file-based session persistence with JSONL
- All contributions must have unit tests; 383 tests currently pass, 0 failures
- JaCoCo 0.8.13 configured for coverage reporting (prepare-agent + report + check goals)

## Progress
### Done
- Provider abstraction with OpenAI, Anthropic, Ollama implementations + tests; ConfigurableProvider interface for runtime API key/base URL configuration
- DefaultAgentLoop with streaming, multi-turn tool calls, permission checking, compaction, plan mode; dedicated AGENT_EXECUTOR and shutdown hook; **120s tool execution timeout** via CompletableFuture.orTimeout
- 13 tools: read, write, edit, glob, grep, shell, webfetch, websearch (DuckDuckGo + Exa AI via OPENCODE_EXA_API_KEY), question, task, lsp, skill, scout (research) + tests
- QuestionTool: non-blocking via ctx.ask() event bus delegation
- Plugin interface + PluginManager with register/unload/list API; event hooks (onSessionStart, onMessage, onToolExecute, onToolResult, onError) fired from DefaultAgentLoop
- Session management: InMemorySession + FileSession with JSONL persist/load; dedicated IO_EXECUTOR + shutdown hook; malformed JSONL lines logged; session titles (auto-generated from first user message, persisted, returned in API); Session.fork(int upToMessageIndex) in both impls
- Full REST API: CRUD sessions, SSE stream, health, providers, agents, skills, usage, plugins, file search, session sharing, conversation search (GET /api/search/sessions?q=), session forking (POST /api/session/{id}/fork), file upload, references (GET /api/references)
- Health endpoint + CORS via CorsWebFilter + @CrossOrigin
- Retry with exponential backoff for provider HTTP calls
- Web UI: streaming token display, markdown rendering, session list/switch/delete with titles, error handling, dark/light theme toggle, file drag-and-drop/upload, @ file search autocomplete, code copy buttons, theme persistence (localStorage), share link display with auto-copy, keyboard shortcuts, auto-resizing textarea, settings panel (auto-scroll, compact, clear), fork button, references viewer
- VSCode extension (TypeScript): start/stop server, chat webview panel, status bar
- Dockerfile (multi-stage), docker-compose.yml, Makefile, .gitignore, GitHub Actions CI
- ChatResponse refactored for multi-tool-call support; AbortSignal.abort() idempotent
- Git undo/redo via SnapshotManager; /undo, /redo, /compact, /models, /unshare, /stats, /export commands
- /init command: ProjectAnalyzer scans project directory, generates AGENTS.md
- @ file references: FileSearchService with fuzzy matching; GET /api/search/files REST endpoint
- Session sharing: SharedSessionService with unshare(); /share, /unshare commands; POST/GET REST endpoints
- Generic OpenAI-compatible provider (not @Component) with configurable name/apiKey/baseUrl/defaultModel; 15 known default base URLs; auto-registered by ProviderConfigurer
- LSP integration: JsonRpc (JSON-RPC 2.0 with Content-Length headers, concurrent request tracking, daemon reader thread), LspServer (process lifecycle, initialize/openDocument/goToDefinition/findReferences/hover/getDiagnostics, 20+ extension language detection), LspService (@Service managing server instances, 24 built-in language server configs, lazy startup, auto-discovery by file extension), LspTool (@Component with actions: definition/references/hover/diagnostics); auto-diagnostics after write/edit tools
- MCP integration: McpClient (JSON-RPC stdio, init/tools/list/tools/call), McpService (@Service managing clients, McpToolProxy wrapping discovered tools as Tool instances in registry, @PostConstruct auto-starts servers), MCP management CLI (/mcp list/start/stop), McpController (GET/POST/DELETE /api/mcp/servers)
- Skill system: Skill record (name/description/path/body), SkillLoader (SKILL.md YAML frontmatter parsing from Path or String), SkillService (@Service scanning skills dirs with dedup, .skill zip support), SkillTool (@Component tool "skill"), /skills and /load commands, GET /api/skills, skill descriptions in system prompt
- Agent type differentiation: ToolFilter in DefaultToolRegistry — explore/architect (read-only), general/ask (read+web), build (all); ARCHITECT and ASK added to BuiltinAgent enum; /agent command switches agents; GET /api/agents lists agents
- Image handling: ImageUtils (resize to MAX_DIMENSION=2048px preserving aspect ratio, toBase64, detectFormat, isImage); file upload endpoint stores binary content as base64 and auto-resizes images; provider image content block support
- Config hierarchy: ConfigLoader merges global (~/.config/opencode/opencode.json) + project config; compactThreshold and compactReserved fields; static getCompactThreshold/getCompactReserved + instance() methods; references config section
- WebSearch Exa AI: OPENCODE_EXA_API_KEY environment variable support, automatic Exa AI API usage
- CLI enhanced: OpenCodeCLI with ANSI colors per event type, real-time streaming display, multi-line input via trailing \, /help command, MCP commands, tool call icons, permission prompts
- **Auto-formatters**: FormatterService runs after write/edit (google-java-format, ruff, rustfmt, gofmt, prettier, ktlint, rubocop, pint, clang-format, dart, shfmt); 24 file extensions supported; no-op when formatter not installed; languageFor() helper method
- **Custom Tools from files**: CustomToolLoader loads .opencode/tools/*.json as script-based tools; CustomToolService auto-registers at startup with reload(); CustomScriptTool implementing Tool\<Metadata\>; JSON schema arg validation
- **References system (@alias)**: ReferenceConfig record with name/description/path/gitRepo/patterns; ReferenceService resolves references with pattern filtering; references injected into system prompt via buildSystemPrompt(); GET /api/references REST endpoints; references section in opencode.json
- **Scout agent**: ScoutTool (@Component) performs web research via DuckDuckGo HTML parsing; configurable query and maxResults parameters; returns formatted research summary
- **ACP Server**: AcpServer (JSON-RPC over stdin/stdout); methods: initialize/process/shutdown; session management with InMemorySession; streaming CompletableFuture-based responses; AcpRunner with "acp" Spring profile
- **Web UI improvements**: settings panel (auto-scroll toggle, compact messages toggle, clear messages button); session forking button; references viewer modal; updateUI() state management function
- **CLI subcommands**: Main.java entry point with serve/web/cli/session(list/show/export)/acp/help subcommands; Spring Boot launcher with profile selection
- **ToolContext.EMPTY** constant for simplified testing
- Test count: **383 tests pass, 0 failures** (up from 356)

### In Progress
- (none)

### Blocked
- WebSearchTool: falls back to HTML parsing when JSON API is unavailable; third-party results page may change format
- OllamaProvider: Tool calling requires model support (e.g., llama3.1+); older Ollama models may fail

## Key Decisions
- Use WebFlux (reactive) instead of Spring MVC for non-blocking SSE streaming
- SSE over WebSocket for simplicity — one-way server→client stream is sufficient for chat
- FileSession stores messages as JSONL + metadata as config.json; dedicated single-thread IO_EXECUTOR prevents ForkJoinPool starvation
- Retry with backoff (1s, 2s) for transient HTTP errors (429, 5xx); non-retryable errors throw immediately
- OpenAICompatibleProvider is NOT a @Component — instantiated dynamically by ProviderConfigurer for each unknown opencode.json entry
- LSP servers spawned on-demand per root+name combo, lazy-initialized on first tool execution; 24 built-in configs use npx
- MCP client uses same JSON-RPC stdio pattern as LSP; tools discovered via tools/list at init, wrapped as Tool instances
- Skill directories deduplicated via HashSet to prevent double-loading when dataDir and workspaceSkills resolve to same path
- Image resize uses java.awt (java.desktop module); max dimension 2048px to fit most provider limits
- Agent tool filtering uses agent name from AgentConfig; dynamically registered MCP tools (ids containing ".") bypass agent restrictions
- Config hierarchy merges global → project config with full array override for providers/agents/mcpServers
- Session forking creates new session via Session.fork(int) and registers with SessionManager.create()
- Tool execution timeout set at 120s using CompletableFuture.orTimeout with AGENT_EXECUTOR
- FormatterService runs formatter as subprocess with 10s timeout; passes file path as last arg; no-op when binary not found
- Custom tools defined as .json files in .opencode/tools/ with name, description, command array, optional args schema
- References configured in opencode.json under `references` object; each key is alias name with path/git/patterns
- ACP server uses "acp" Spring profile; JSON-RPC 2.0 over stdin/stdout with newline-delimited messages

## Next Steps
- **(low) IDE extensions**: VSCode improvements, Cursor, Zed, JetBrains plugins
- **(low) GitHub Actions integration**: /opencode on PR/issue comments
- **(low) Desktop app**: Electron packaging
- **(medium) Full TUI**: Themes, keybindings, command palette, session browser

## Critical Context
- **383 tests pass, 0 failures** across 60+ test classes
- Compilation: mvn compile clean, TypeScript extension tsc clean
- JaCoCo 0.8.13 configured in pom.xml with prepare-agent, report (package phase), and check (0% minimum threshold)
- No Spring Security — API is fully open; no spring-boot-starter-validation — parameter validation is manual
- No spring-boot-starter-actuator — only manual /api/health endpoint
- OPENAI_API_KEY, ANTHROPIC_API_KEY, DEEPSEEK_API_KEY, OPENROUTER_API_KEY, GROQ_API_KEY, TOGETHER_API_KEY, OPENCODE_EXA_API_KEY environment variables may be required
- Server starts on localhost:8080; web UI at /, API at /api/
- CLI profile: `--spring.profiles.active=cli`; ACP profile: `--spring.profiles.active=acp`
- Entry points: `Main` class (subcommand dispatcher) or `OpenCodeApplication` class (direct Spring Boot)
- New REST endpoints: POST /api/session/{id}/fork, GET /api/search/sessions, GET /api/agents, GET /api/skills, GET/POST/DELETE /api/mcp/servers, GET /api/references, GET /api/references/{name}/resolve, GET /api/references/resolve-all
- MCP servers defined in opencode.json under `mcpServers` key; auto-started asynchronously with 10s timeout
- Global config at ~/.config/opencode/opencode.json merged with project config
- References configured in opencode.json under `references` key as map of alias → {path, gitRepo/git, patterns, description}
- Custom tools directory: .opencode/tools/*.json; auto-discovered on startup

## Relevant Files
- `src/main/java/io/opencode/Main.java`: CLI subcommand entry point (serve/web/cli/session/acp/help)
- `src/main/java/io/opencode/core/agent/impl/DefaultAgentLoop.java`: Main agent loop + auto-formatters + references in system prompt
- `src/main/java/io/opencode/core/agent/BuiltinAgent.java`: Enum (build/plan/general/explore/compaction/title/summary/architect/ask)
- `src/main/java/io/opencode/core/tool/DefaultToolRegistry.java`: ToolFilter by agent name
- `src/main/java/io/opencode/core/tool/CustomToolLoader.java`: Loads .opencode/tools/*.json as CustomScriptTool
- `src/main/java/io/opencode/core/tool/util/CustomToolService.java`: @PostConstruct auto-registration + reload
- `src/main/java/io/opencode/core/tool/util/ReferenceService.java`: Resolves @alias references from config
- `src/main/java/io/opencode/core/tool/impl/ScoutTool.java`: Web research tool
- `src/main/java/io/opencode/core/formatter/FormatterService.java`: Auto-formatter for 24 file extensions
- `src/main/java/io/opencode/core/acp/AcpServer.java`: JSON-RPC ACP server over stdin/stdout
- `src/main/java/io/opencode/core/config/ReferenceConfig.java`: Reference record (name/description/path/gitRepo/patterns)
- `src/main/java/io/opencode/core/config/OpenCodeConfig.java`: Added references, static instance/compactThreshold/compactReserved
- `src/main/java/io/opencode/core/config/ConfigLoader.java`: Loads references section from opencode.json
- `src/main/java/io/opencode/core/tool/ToolContext.java`: Added EMPTY constant
- `src/main/java/io/opencode/server/AppConfiguration.java`: Wires ReferenceService from config
- `src/main/java/io/opencode/server/ReferenceController.java`: GET /api/references endpoints
- `src/main/java/io/opencode/server/AcpRunner.java`: ACP server with "acp" profile
- `src/main/resources/static/index.html`: Web UI with settings/fork/refs
