package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class KeywordSearchController {

    private final KeywordSearchService keywordSearchService;

    public KeywordSearchController(KeywordSearchService keywordSearchService) {
        this.keywordSearchService = keywordSearchService;
    }

    @PostMapping("/keyword")
    public ResponseEntity<KeywordSearchResult> searchKeyword(@RequestBody VectorSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()
                || request.getRepositoryPath() == null || request.getRepositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 10;
        return ResponseEntity.ok(keywordSearchService.search(
                request.getQuery(), request.getRepositoryPath(), limit));
    }
}
