package com.tokenopt.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class RawChunkStore {

    private final Cache<String, List<String>> chunkCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    public String store(String rawContent) {
        String chunkId = "chk_" + UUID.randomUUID().toString().substring(0, 6);
        List<String> lines = Arrays.asList(rawContent.split("\\R"));
        chunkCache.put(chunkId, lines);
        return chunkId;
    }

    public String getLines(String chunkId, int startLine, int endLine) {
        List<String> lines = chunkCache.getIfPresent(chunkId);
        if (lines == null) {
            return "Chunk ID '" + chunkId + "' expired or not found.";
        }
        int safeStart = Math.max(1, startLine);
        int safeEnd = Math.min(lines.size(), endLine);
        if (safeStart > safeEnd) {
            return "Invalid line range requested.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = safeStart; i <= safeEnd; i++) {
            sb.append(String.format("%4d: %s%n", i, lines.get(i - 1)));
        }
        return sb.toString();
    }
}