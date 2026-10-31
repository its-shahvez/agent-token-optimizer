Markdown# ⚡ Agent Token Optimizer (Java / Spring Boot MCP)

> **Reduce your AI coding agent's context usage by 73%–99.9% with zero LLM overhead.**[cite: 2, 6]  
> Built with **Java 21**, **Spring Boot 3.4**, and **Official Spring AI MCP Server**. Works out-of-the-box with **Claude Code**, **Cursor**, **Windsurf**, **Cline**, and **MCP Inspector**.

[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MCP Compatible](https://img.shields.io/badge/MCP-SSE%20Verified-blue.svg)](https://modelcontextprotocol.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🎯 Why This Exists

When AI coding agents (`Claude Code`, `Cursor`, `Cline`, etc.) debug JVM applications, a single `mvn test` output, verbose production log, or Spring Boot stack trace easily burns **20,000–280,000 tokens** of context window—inflating API costs and degrading LLM reasoning[cite: 2].

> 💡 **Industry Context (Input vs. Output Optimization):**  
> While **Cloudflare's Code Mode** solves MCP *tool-definition bloat* on the **input side**, **Agent Token Optimizer** solves *tool-output bloat* (massive logs, AST source files, and JVM stack traces) on the **response side**.

**Agent Token Optimizer** acts as a **deterministic, zero-LLM interceptor** between your tools and the AI agent:
- **No LLM in the loop:** 100% pure Java deterministic compression (JavaParser AST + Drain-style log clustering + stack frame pruning).
- **Sub-second latency:** Compresses thousands of lines in milliseconds[cite: 2].
- **Lossless Drill-Down Cache:** Every omitted block leaves a `chunkId` pointer (`ref: chk_434da1, lines 3-6`). If the root cause actually lives inside a Spring Security filter or CGLIB proxy, the agent calls `expand_section` to fetch those exact raw lines on demand[cite: 3, 4].

---

## 📊 Live Benchmark Results

Measured using exact BPE token counting via [`JTokkit`](https://github.com/knuddelsgmbh/jtokkit) (`CL100K_BASE`):

| Workload Type | Original Tokens | Compressed Tokens | Token Savings | Latency | Strategy Used |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **5,000-Line Prod Logs** | 281,086 | **222** | **99.9%** | **352 ms** | `LogCompressor-TemplateDedup`[cite: 2] |
| **Spring Boot StackTrace** (166 frames) | 3,136 | **160** | **94.9%** | **8 ms** | `StackTraceCompressor`[cite: 2] |
| **40-Method Java Class** (AST) | 4,123 | **954** | **76.9%** | **450 ms** | `CodeCompressor-AST`[cite: 2] |

---

## 🧪 Verified Live MCP Protocol Tests (MCP Inspector)

Verified end-to-end over SSE (`http://localhost:8081/sse`) using the official `@modelcontextprotocol/inspector`[cite: 4, 5, 6]:

### 1. Stack Trace Pruning (`compress_output`) + Lossless Recovery (`expand_section`)

<details>
<summary><b>View Raw Input Stack Trace (Click to expand)</b></summary>

```text
java.lang.IllegalStateException: Payment Gateway Timeout
	at com.tokenopt.order.OrderController.checkout(OrderController.java:84)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
Caused by: java.net.SocketTimeoutException: Read timed out
	at com.tokenopt.payment.StripeClient.charge(StripeClient.java:45)
	at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)
Compressed MCP Output (chunkId: "chk_434da1"):
Application packages (com.tokenopt.*) and Caused by: chains survive untouched, while internal framework/proxy frames are folded into a lossless pointer:   Plaintextjava.lang.IllegalStateException: Payment Gateway Timeout
	at com.tokenopt.order.OrderController.checkout(OrderController.java:84)
	[... 4 internal/framework frames omitted (ref: chk_434da1, lines 3-6) ...]
Caused by: java.net.SocketTimeoutException: Read timed out
	at com.tokenopt.payment.StripeClient.charge(StripeClient.java:45)
	[... 1 internal/framework frames omitted (ref: chk_434da1, lines 9-9) ...]
On-Demand Lossless Drill-Down (expand_section("chk_434da1", 3, 6)):[cite: 5]
If the AI agent suspects an issue inside the omitted Spring AOP or Tomcat filter chain, it retrieves the exact raw lines from the Caffeine cache:   Plaintext   3: 	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)
   4: 	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
   5: 	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178)
   6: 	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
2. Live AST Source File Compression (read_compressed_file)Running read_compressed_file on CodeCompressor.java reduces 1,106 tokens down to 299 tokens (73% reduction) while keeping all method signatures and exact line boundaries:   Javapublic class CodeCompressor implements CompressionStrategy {
    private final TokenEstimator tokenEstimator;
    private final RawChunkStore chunkStore;
    public CodeCompressor(TokenEstimator tokenEstimator, RawChunkStore chunkStore) { /* L18-L21 */ }
    public CompressionResult compress(String rawCode, CompressionLevel level) { /* L23-L45 */ }
    private String buildAstSkeleton(CompilationUnit cu, String chunkId, CompressionLevel level) { /* L47-L66 */ }
    private void appendTypeSkeleton(TypeDeclaration<?> type, StringBuilder sb, CompressionLevel level, String indent) { /* L68-L92 */ }
    private void appendCallableSignature(CallableDeclaration<?> callable, StringBuilder sb, String indent) { /* L94-L102 */ }
    private String buildFallbackSkeleton(String rawCode, String chunkId) { /* L104-L118 */ }
}
🏗️ 4-Layer ArchitecturePlaintext┌─────────────────────────────────────────────────────────────────┐
│ Layer 4: MCP Server & REST Adapter (Spring AI MCP + WebMVC)     │
│  Tools: compress_output | read_compressed_file                  │
│         run_and_compress | expand_section                       │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 3: Dynamic Token Budget Manager (BudgetPolicy)            │
│  Auto-detects payload type (LOG / CODE / STACK_TRACE) & selects │
│  CONSERVATIVE, BALANCED, or AGGRESSIVE policy per token budget. │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 2: Deterministic Compression Engine (CompressionStrategy) │
│  ├── StackTraceCompressor (Framework/Proxy noise pruning)       │
│  ├── CodeCompressor       (JavaParser AST method-body stripping)│
│  └── LogCompressor        (Timestamp/UUID/Num masking + Dedup)  │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: Lossless In-Memory Chunk Store & BPE Tokenizer         │
│  Caffeine TTL Cache (chk_xxxxxx) + JTokkit CL100K_BASE Counter  │
└─────────────────────────────────────────────────────────────────┘
🛠️ Exposed MCP ToolsOnce connected, your AI agent automatically gains 4 tools:   run_and_compress(command, workingDirectory, maxTokenBudget)Executes build/test commands (e.g., mvn test, gradle check) and returns compressed logs + root-cause stack traces without overflowing context.   read_compressed_file(filePath, maxTokenBudget)Parses source files into an AST skeleton (keeping package, imports, annotations, fields, and method signatures with /* L23-L45 */ markers).   compress_output(rawContent, sourceType, maxTokenBudget)Compresses arbitrary raw logs, stack traces, or code snippets.   expand_section(chunkId, startLine, endLine)Lossless fallback: retrieves exact raw lines from the Caffeine cache using the chunkId reference.   🚀 Quick Start1. Clone & Run ServerBashgit clone [https://github.com/its-shahvez/agent-token-optimizer.git](https://github.com/its-shahvez/agent-token-optimizer.git)
cd agent-token-optimizer
./mvnw spring-boot:run
The server starts on http://localhost:8081 and automatically executes the live benchmark suite on startup.2. Connect to Cursor / Claude Desktop / Cline (MCP SSE)Add the following to your .cursor/mcp.json or MCP client configuration:JSON{
  "mcpServers": {
    "java-token-optimizer": {
      "url": "http://localhost:8081/sse"
    }
  }
}
3. Test Visually with Official MCP Inspector (No Paid IDE Required)Bashnpx @modelcontextprotocol/inspector
Select SSE transport and connect to http://localhost:8081/sse to interactively test all 4 tools in your browser[cite: 4].4. Test via REST API (cURL)Bashcurl -X POST http://localhost:8081/api/v1/compress \
  -H "Content-Type: application/json" \
  -d '{
    "rawContent": "java.lang.NullPointerException: Cannot invoke...\n\tat com.tokenopt.App.main(App.java:14)\n\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)",
    "sourceType": "AUTO",
    "maxTokenBudget": 300
  }'
Drill-down into omitted lines:Bashcurl "http://localhost:8081/api/v1/expand/chk_434da1?startLine=3&endLine=6"



📜 License
MIT License. Free for personal and commercial use.


---

