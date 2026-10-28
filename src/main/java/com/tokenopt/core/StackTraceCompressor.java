package com.tokenopt.core;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StackTraceCompressor implements CompressionStrategy{

    // Woh internal packages jo AI Agent ke kaam ke nahi hote (Noise)
    private static final List<String> NOISE_PACKAGES = List.of(
            "org.springframework.aop.",
            "org.springframework.cglib.",
            "org.springframework.transaction.",
            "org.springframework.security.web.FilterChainProxy",
            "org.springframework.web.filter.",
            "org.springframework.web.servlet.FrameworkServlet",
            "org.apache.catalina.",
            "org.apache.coyote.",
            "org.apache.tomcat.",
            "java.base/jdk.internal.",
            "java.base/java.lang.reflect.",
            "jdk.proxy",
            "org.junit.platform.",
            "org.junit.jupiter.engine.",
            "org.gradle.",
            "org.apache.maven.surefire."
    );

    private final TokenEstimator tokenEstimator;
    private final RawChunkStore chunkStore;

    public StackTraceCompressor(TokenEstimator tokenEstimator, RawChunkStore chunkStore) {
        this.tokenEstimator = tokenEstimator;
        this.chunkStore = chunkStore;
    }

    public CompressionResult compress(String rawStackTrace, CompressionLevel level) {
        if (rawStackTrace == null || rawStackTrace.isBlank()) {
            return CompressionResult.of("none", "", 0, 0, "StackTraceCompressor");
        }

        String chunkId = chunkStore.store(rawStackTrace);
        int origTokens = tokenEstimator.countTokens(rawStackTrace);

        String[] lines = rawStackTrace.split("\\R");
        StringBuilder out = new StringBuilder();

        int omittedFrames = 0;
        int firstOmittedLine = -1;
        int keptAppFrames = 0;
        int maxAppFramesPerSection = (level == CompressionLevel.AGGRESSIVE) ? 3 : 8;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            boolean isStackFrame = trimmed.startsWith("at ") || trimmed.startsWith("... ");

            if (isStackFrame) {
                boolean isNoise = isNoiseFrame(trimmed);

                if (isNoise || keptAppFrames >= maxAppFramesPerSection) {
                    if (omittedFrames == 0) {
                        firstOmittedLine = i + 1;
                    }
                    omittedFrames++;
                } else {
                    flushOmitted(out, omittedFrames, chunkId, firstOmittedLine, i);
                    omittedFrames = 0;
                    out.append(line).append("\n");
                    keptAppFrames++;
                }
            } else {
                // Exception Header ya "Caused by:" line aayi hai -> counter reset karein!
                flushOmitted(out, omittedFrames, chunkId, firstOmittedLine, i);
                omittedFrames = 0;
                keptAppFrames = 0;
                out.append(line).append("\n");
            }
        }

        flushOmitted(out, omittedFrames, chunkId, firstOmittedLine, lines.length);

        String compressed = out.toString().trim();
        int compTokens = tokenEstimator.countTokens(compressed);

        return CompressionResult.of(
                chunkId,
                compressed,
                origTokens,
                compTokens,
                "StackTraceCompressor(" + level.name() + ")"
        );
    }

    private boolean isNoiseFrame(String trimmedFrame) {
        if (trimmedFrame.startsWith("... ")) {
            return true;
        }
        for (String pkg : NOISE_PACKAGES) {
            if (trimmedFrame.contains(pkg)) {
                return true;
            }
        }
        return false;
    }

    private void flushOmitted(StringBuilder out, int count, String chunkId, int startLine, int endLine) {
        if (count > 0) {
            out.append(String.format(
                    "\t[... %d internal/framework frames omitted (ref: %s, lines %d-%d) ...]%n",
                    count, chunkId, startLine, endLine
            ));
        }
    }
}