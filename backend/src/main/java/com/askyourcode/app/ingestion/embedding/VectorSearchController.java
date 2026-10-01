package com.askyourcode.app.ingestion.embedding;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public VectorSearchController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping("/vector")
    public ResponseEntity<VectorSearchResult> searchVector(@RequestBody VectorSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (request.getRepositoryPath() == null || request.getRepositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 10;

        VectorSearchResult result = vectorSearchService.search(
            request.getQuery(),
            request.getRepositoryPath(),
            limit
        );

        return ResponseEntity.ok(result);
    }
}