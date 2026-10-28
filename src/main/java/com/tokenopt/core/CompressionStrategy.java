package com.tokenopt.core;

public interface CompressionStrategy {
    CompressionResult compress(String rawContent, CompressionLevel level);
}