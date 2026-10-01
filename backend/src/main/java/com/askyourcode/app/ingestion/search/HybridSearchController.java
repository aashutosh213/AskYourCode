package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;

    public HybridSearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping("/hybrid")
    public ResponseEntity<HybridSearchResult> searchHybrid(@RequestBody VectorSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()
                || request.getRepositoryPath() == null || request.getRepositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 10;
        return ResponseEntity.ok(hybridSearchService.search(
                request.getQuery(), request.getRepositoryPath(), limit));
    }
}
