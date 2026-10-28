package com.tokenopt.core;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component


public class LogCompressor implements CompressionStrategy {


    private static final Pattern ISO_TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );
    private static final Pattern HEX_OR_NUM = Pattern.compile("0x[0-9a-fA-F]+|\\d+");

    private final TokenEstimator tokenEstimator;
    private final RawChunkStore chunkStore;

    public LogCompressor(TokenEstimator tokenEstimator, RawChunkStore chunkStore) {
        this.tokenEstimator = tokenEstimator;
        this.chunkStore = chunkStore;
    }

    public CompressionResult compress(String rawLogs, CompressionLevel level) {
        if (rawLogs == null || rawLogs.isBlank()) {
            return CompressionResult.of("none", "", 0, 0, "LogCompressor");
        }

        String chunkId = chunkStore.store(rawLogs);
        int origTokens = tokenEstimator.countTokens(rawLogs);

        String[] lines = rawLogs.split("\\R");
        Map<String, LogGroup> patternGroups = new LinkedHashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;

            if (level == CompressionLevel.AGGRESSIVE && isDebugOrTrace(line)) {
                continue;
            }

            String signature = normalizeLogSignature(line);
            int lineNum = i + 1;

            patternGroups.computeIfAbsent(signature, k -> new LogGroup(line, lineNum))
                    .recordOccurrence(lineNum);
        }

        StringBuilder out = new StringBuilder();
        out.append(String.format("[Log Summary | chunkId=%s | totalLines=%d | uniquePatterns=%d]%n",
                chunkId, lines.length, patternGroups.size()));

        for (LogGroup group : patternGroups.values()) {
            if (group.count == 1) {
                out.append(String.format("L%d: %s%n", group.firstLine, group.sampleLine));
            } else {
                out.append(String.format("L%d..L%d [Repeated %dx]: %s%n",
                        group.firstLine, group.lastLine, group.count, group.sampleLine));
            }
        }

        String compressed = out.toString().trim();
        int compTokens = tokenEstimator.countTokens(compressed);

        return CompressionResult.of(
                chunkId,
                compressed,
                origTokens,
                compTokens,
                "LogCompressor-TemplateDedup(" + level.name() + ")"
        );
    }

    private boolean isDebugOrTrace(String line) {
        return line.contains(" DEBUG ") || line.contains(" TRACE ")
                || line.startsWith("DEBUG") || line.startsWith("TRACE");
    }

    private String normalizeLogSignature(String line) {
        String s = ISO_TIMESTAMP.matcher(line).replaceAll("<TS>");
        s = UUID_PATTERN.matcher(s).replaceAll("<UUID>");
        s = HEX_OR_NUM.matcher(s).replaceAll("<NUM>");
        return s.trim();
    }

    private static class LogGroup {
        final String sampleLine;
        final int firstLine;
        int lastLine;
        int count = 0;

        LogGroup(String sampleLine, int firstLine) {
            this.sampleLine = sampleLine;
            this.firstLine = firstLine;
            this.lastLine = firstLine;
        }

        void recordOccurrence(int lineNum) {
            this.count++;
            this.lastLine = lineNum;
        }
    }
}