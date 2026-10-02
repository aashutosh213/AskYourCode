package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class RerankedSearchController {
    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;

    public RerankedSearchController(HybridSearchService hybridSearchService,
                                    RerankingService rerankingService) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
    }

    @PostMapping("/reranked")
    public ResponseEntity<RerankedSearchResult> searchReranked(@RequestBody VectorSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()
                || request.getRepositoryPath() == null || request.getRepositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 10;
        int candidateLimit = Math.min(Math.max(limit * 2, 10), 100);
        HybridSearchResult hybrid = hybridSearchService.search(
                request.getQuery(), request.getRepositoryPath(), candidateLimit);
        return ResponseEntity.ok(rerankingService.rerank(request.getQuery(), hybrid, limit));
    }
}
