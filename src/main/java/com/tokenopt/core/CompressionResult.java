package com.tokenopt.core;

public record CompressionResult(
        String chunkId,
        String compressedContent,
        int originalTokens,
        int compressedTokens,
        double reductionPercentage,
        String strategyUsed
) {
    public static CompressionResult of(
            String chunkId,
            String compressedContent,
    int origTokens,
    int compTokens,
    String strategy
    ) {
        double reduction = origTokens <= 0 ? 0.0 :
                Math.round(((double) (origTokens - compTokens) / origTokens) * 1000.0) / 10.0;
        return new CompressionResult(
                chunkId,
                compressedContent,
                origTokens,
                compTokens,
                Math.max(0.0, reduction),
                strategy
        );
    }
}