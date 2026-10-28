package com.tokenopt.mcp;

import com.tokenopt.budget.TokenBudgetManager;
import com.tokenopt.core.CompressionResult;
import com.tokenopt.core.RawChunkStore;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class OptimizerMcpTools {

    private final TokenBudgetManager budgetManager;
    private final RawChunkStore chunkStore;

    public OptimizerMcpTools(TokenBudgetManager budgetManager, RawChunkStore chunkStore) {
        this.budgetManager = budgetManager;
        this.chunkStore = chunkStore;
    }

    @Tool(description = "Compress large tool outputs (logs, stack traces, or source code) to save 60-90% LLM tokens while preserving root causes and structure.")
    public CompressionResult compress_output(
            @ToolParam(description = "Raw text content to compress") String rawContent,
            @ToolParam(description = "Type: AUTO, LOG, CODE, or STACK_TRACE") String sourceType,
            @ToolParam(description = "Optional target token budget (e.g. 500)") Integer maxTokenBudget
    ) {
        return budgetManager.optimize(rawContent, sourceType, maxTokenBudget);
    }

    @Tool(description = "Read a source code or log file from disk and return an AST-compressed skeleton to save context window tokens.")
    public CompressionResult read_compressed_file(
            @ToolParam(description = "Absolute or relative file path") String filePath,
            @ToolParam(description = "Optional max token budget") Integer maxTokenBudget
    ) {
        try {
            String raw = Files.readString(Path.of(filePath));
            String type = filePath.endsWith(".java") ? "CODE" : "AUTO";
            return budgetManager.optimize(raw, type, maxTokenBudget);
        } catch (Exception e) {
            return CompressionResult.of("error", "Failed to read file: " + e.getMessage(), 0, 0, "Error");
        }
    }

    @Tool(description = "Execute a shell/build command (e.g. mvn test, gradle build) and return its compressed output so large build logs do not overflow LLM context.")
    public CompressionResult run_and_compress(
            @ToolParam(description = "Command to execute, e.g., 'mvn test'") String command,
            @ToolParam(description = "Working directory path (optional)") String workingDirectory,
            @ToolParam(description = "Optional max token budget") Integer maxTokenBudget
    ) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);

            if (workingDirectory != null && !workingDirectory.isBlank()) {
                pb.directory(new File(workingDirectory));
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(120, TimeUnit.SECONDS);

            return budgetManager.optimize(output, "AUTO", maxTokenBudget);
        } catch (Exception e) {
            return CompressionResult.of("error", "Command execution failed: " + e.getMessage(), 0, 0, "Error");
        }
    }

    @Tool(description = "Lossless drill-down: Retrieve exact original lines from a previously compressed chunkId when detailed inspection is needed.")
    public String expand_section(
            @ToolParam(description = "The chunkId returned by a previous compression call (e.g. chk_a1b2c3)") String chunkId,
            @ToolParam(description = "Start line number (1-indexed)") int startLine,
            @ToolParam(description = "End line number (inclusive)") int endLine
    ) {
        return chunkStore.getLines(chunkId, startLine, endLine);
    }

    @Configuration
    static class McpConfig {
        @Bean
        public ToolCallbackProvider optimizerToolCallbackProvider(OptimizerMcpTools tools) {
            return MethodToolCallbackProvider.builder().toolObjects(tools).build();
        }
    }
}