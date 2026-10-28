package com.tokenopt.budget;

import com.tokenopt.core.CompressionLevel;

public interface BudgetPolicy {
    CompressionLevel evaluateLevel(String rawContent, Integer maxTokenBudget);
}