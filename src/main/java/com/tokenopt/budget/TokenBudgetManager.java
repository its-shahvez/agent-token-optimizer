package com.tokenopt.budget;

import com.tokenopt.core.*;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenBudgetManager implements BudgetPolicy {

    private final StackTraceCompressor stackTraceCompressor;
    private final LogCompressor logCompressor;
    private final CodeCompressor codeCompressor;

    private final AtomicLong totalOriginalTokens = new AtomicLong(0);
    private final AtomicLong totalCompressedTokens = new AtomicLong(0);

    public TokenBudgetManager(
            StackTraceCompressor stackTraceCompressor,
            LogCompressor logCompressor,
            CodeCompressor codeCompressor
    ) {
        this.stackTraceCompressor = stackTraceCompressor;
        this.logCompressor = logCompressor;
        this.codeCompressor = codeCompressor;
    }

    public CompressionResult optimize(String rawContent, String sourceType, Integer maxTokenBudget) {
        CompressionLevel level = evaluateLevel(rawContent, maxTokenBudget);
        String resolvedType = (sourceType == null || sourceType.equalsIgnoreCase("AUTO"))
                ? detectType(rawContent)
                : sourceType.toUpperCase();

        CompressionResult result = switch (resolvedType) {
            case "STACK_TRACE" -> stackTraceCompressor.compress(rawContent, level);
            case "CODE" -> codeCompressor.compress(rawContent, level);
            default -> logCompressor.compress(rawContent, level);
        };

        if (maxTokenBudget != null && maxTokenBudget > 0
                && result.compressedTokens() > maxTokenBudget
                && level != CompressionLevel.AGGRESSIVE) {
            result = switch (resolvedType) {
                case "STACK_TRACE" -> stackTraceCompressor.compress(rawContent, CompressionLevel.AGGRESSIVE);
                case "CODE" -> codeCompressor.compress(rawContent, CompressionLevel.AGGRESSIVE);
                default -> logCompressor.compress(rawContent, CompressionLevel.AGGRESSIVE);
            };
        }

        totalOriginalTokens.addAndGet(result.originalTokens());
        totalCompressedTokens.addAndGet(result.compressedTokens());
        return result;
    }

    @Override
    public CompressionLevel evaluateLevel(String rawContent, Integer maxTokenBudget) {
        if (maxTokenBudget == null || maxTokenBudget <= 0) {
            return CompressionLevel.BALANCED;
        }
        int estimatedOrig = rawContent.length() / 4;
        if (estimatedOrig > maxTokenBudget * 3) {
            return CompressionLevel.AGGRESSIVE;
        } else if (estimatedOrig <= maxTokenBudget) {
            return CompressionLevel.CONSERVATIVE;
        }
        return CompressionLevel.BALANCED;
    }

    public String getSavingsReport() {
        long orig = totalOriginalTokens.get();
        long comp = totalCompressedTokens.get();
        long saved = Math.max(0, orig - comp);
        double pct = orig == 0 ? 0.0 : ((double) saved / orig) * 100.0;
        double dollarsSaved = (saved / 1_000_000.0) * 3.00;
        return String.format(
                "Session Stats -> Original: %,d tokens | Compressed: %,d tokens | Saved: %,d tokens (%.1f%%) | Est. Cost Saved: $%.4f",
                orig, comp, saved, pct, dollarsSaved
        );
    }

    private String detectType(String text) {
        if (text.contains("\tat ") && (text.contains("Exception") || text.contains("Error"))) {
            return "STACK_TRACE";
        }
        if (text.contains("package ") || text.contains("public class ") || text.contains("import java.")) {
            return "CODE";
        }
        return "LOG";
    }
}