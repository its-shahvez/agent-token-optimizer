package com.tokenopt.api;

import com.tokenopt.budget.TokenBudgetManager;
import com.tokenopt.core.CompressionResult;
import com.tokenopt.core.RawChunkStore;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CompressionController {

    private final TokenBudgetManager budgetManager;
    private final RawChunkStore chunkStore;

    public CompressionController(TokenBudgetManager budgetManager, RawChunkStore chunkStore) {
        this.budgetManager = budgetManager;
        this.chunkStore = chunkStore;
    }

    public record CompressRequest(String rawContent, String sourceType, Integer maxTokenBudget) {}

    @PostMapping("/compress")
    public CompressionResult compress(@RequestBody CompressRequest request) {
        return budgetManager.optimize(
                request.rawContent(),
                request.sourceType(),
                request.maxTokenBudget()
        );
    }

    @GetMapping("/expand/{chunkId}")
    public String expand(
            @PathVariable String chunkId,
            @RequestParam(defaultValue = "1") int startLine,
            @RequestParam(defaultValue = "50") int endLine
    ) {
        return chunkStore.getLines(chunkId, startLine, endLine);
    }
}