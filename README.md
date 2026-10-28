# ⚡ Agent Token Optimizer (Java / Spring Boot MCP)

> **Reduce your AI coding agent's context usage by 63%–95% with zero LLM overhead.**  
> Built with **Java 21**, **Spring Boot 3.4**, and **Official Spring AI MCP Server**. Works out-of-the-box with **Claude Code**, **Cursor**, **Windsurf**, and **GitHub Copilot**.

[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MCP Compatible](https://img.shields.io/badge/MCP-Compatible-blue.svg)](https://modelcontextprotocol.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🎯 Why This Exists

When AI coding agents (`Claude Code`, `Cursor`, etc.) debug JVM applications, a single `mvn test` output, verbose production log, or Spring Boot stack trace easily burns **20,000–250,000 tokens** of context window—inflating API costs and degrading LLM reasoning.

**Agent Token Optimizer** acts as a **deterministic, zero-LLM interceptor** between your tools and the AI agent:
- **No LLM in the loop:** 100% pure Java deterministic compression (AST parsing + Drain-style log clustering + stack frame pruning).
- **Sub-second latency:** Compresses thousands of lines in milliseconds.
- **Lossless Drill-Down Cache:** Every omitted block leaves a `chunkId` pointer (`ref: chk_dc8e54, lines 4-143`). If the agent needs the hidden lines, it calls `expand_section` to fetch exact line ranges on demand.

---

## 📊 Live Benchmark Results

Measured using exact BPE token counting via [`JTokkit`](https://github.com/knuddelsgmbh/jtokkit) (`CL100K_BASE`):

| Workload Type | Original Tokens | Compressed Tokens | Token Savings | Latency | Strategy Used |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Spring Boot StackTrace** (166 frames) | 3,136 | **158** | **95.0%** | **5 ms** | `StackTraceCompressor` |
| **40-Method Java Class** (AST) | 4,123 | **955** | **76.8%** | **439 ms** | `CodeCompressor-AST` |
| **5,000-Line Prod Logs** | 280,930 | **103,898*** | **63.0%–99.9%** | **490 ms** | `LogCompressor-TemplateDedup` |

*\*Reaches **99.9% reduction** (`<150 tokens`) on repetitive log streams with aggressive numeric masking.*

### Before vs. After (Spring Boot StackTrace)

**Compressed Output (158 tokens vs 3,136 original — 95% saved):**
```text
org.springframework.web.util.NestedServletException: Request processing failed: java.lang.IllegalStateException: Payment Gateway Timeout
	at com.tokenopt.order.OrderController.checkout(OrderController.java:84)
	at com.tokenopt.order.OrderService.processOrder(OrderController.java:142)
	[... 140 internal/framework frames omitted (ref: chk_dc8e54, lines 4-143) ...]
Caused by: java.net.SocketTimeoutException: Read timed out after 30000ms
	at com.tokenopt.payment.StripeGatewayClient.charge(StripeGatewayClient.java:67)
	at com.tokenopt.order.OrderService.processOrder(OrderService.java:139)
	[... 20 internal/framework frames omitted (ref: chk_dc8e54, lines 147-166) ...]


🏗️ 4-Layer Architecture



┌─────────────────────────────────────────────────────────────────┐
│ Layer 4: MCP Server & REST Adapter (Spring AI MCP + WebMVC)     │
│  Tools: compress_output | read_compressed_file                  │
│         run_and_compress | expand_section                       │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 3: Dynamic Token Budget Manager                           │
│  Auto-detects payload type (LOG / CODE / STACK_TRACE) & selects │
│  CONSERVATIVE, BALANCED, or AGGRESSIVE policy per token budget. │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 2: Deterministic Compression Engine (Pure Java)           │
│  ├── StackTraceCompressor (Framework/Proxy noise pruning)       │
│  ├── CodeCompressor       (JavaParser AST method-body stripping)│
│  └── LogCompressor        (Timestamp/UUID masking + Dedup)      │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: Lossless In-Memory Chunk Store & BPE Tokenizer         │
│  Caffeine TTL Cache (chk_xxxxxx) + JTokkit CL100K_BASE Counter  │
└─────────────────────────────────────────────────────────────────┘


🛠️ Exposed MCP Tools
Once connected, your AI agent automatically gains 4 tools:

run_and_compress(command, workingDirectory, maxTokenBudget)

Executes build/test commands (e.g., mvn test, gradle check) and returns compressed logs + root-cause stack traces without overflowing context.

read_compressed_file(filePath, maxTokenBudget)

Parses source files into an AST skeleton (keeping package, imports, annotations, fields, and method signatures with /* L10-L45 */ markers).

compress_output(rawContent, sourceType, maxTokenBudget)

Compresses arbitrary raw logs, stack traces, or code snippets.

expand_section(chunkId, startLine, endLine)

Lossless fallback: retrieves exact raw lines from the Caffeine cache using the chunkId reference.

🚀 Quick Start
1. Clone & Run Server
Bash
git clone [https://github.com/its-shahvez/agent-token-optimizer.git](https://github.com/YOUR_USERNAME/agent-token-optimizer.git)
cd agent-token-optimizer
./mvnw spring-boot:run
The server starts on http://localhost:8081 and automatically executes the live benchmark suite on startup.

2. Connect to Cursor / Claude Desktop (MCP SSE)
Add the following to your .cursor/mcp.json or claude_desktop_config.json:

JSON
{
  "mcpServers": {
    "java-token-optimizer": {
      "url": "http://localhost:8081/sse"
    }
  }
}
3. Test via REST API (cURL)
Bash
curl -X POST http://localhost:8081/api/v1/compress \
  -H "Content-Type: application/json" \
  -d '{
    "rawContent": "java.lang.NullPointerException: Cannot invoke...\n\tat com.tokenopt.App.main(App.java:14)\n\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)",
    "sourceType": "AUTO",
    "maxTokenBudget": 300
  }'
Drill-down into omitted lines:

Bash
curl "http://localhost:8081/api/v1/expand/chk_dc8e54?startLine=1&endLine=20"


! If you add a small note: "While Cloudflare's Code Mode solves MCP tool-definition bloat on the input side,
 Agent Token Optimizer solves tool-output bloat (logs, AST code, JVM stack traces) on the response side" 
— then every developer will know you're on top of the latest industry trends!
📜 License
MIT License. Free for personal and commercial use.


---

