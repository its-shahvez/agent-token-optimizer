package com.tokenopt.core;

public enum CompressionLevel {
    CONSERVATIVE, // Thoda context rakhega (jab token budget zyada ho)
    BALANCED,     // Default: Standard deduplication + framework frame trimming
    AGGRESSIVE    // Maximum bachat: Sirf root cause aur signatures (jab context full hone wala ho)
}