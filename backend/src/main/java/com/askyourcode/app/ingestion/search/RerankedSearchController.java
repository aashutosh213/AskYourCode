package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/search")
public class RerankedSearchController {
    private final SearchJobService searchJobService;

    public RerankedSearchController(SearchJobService searchJobService) {
        this.searchJobService = searchJobService;
    }

    @PostMapping("/reranked")
    public ResponseEntity<SearchJob> searchReranked(@RequestBody VectorSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()
                || request.getRepositoryPath() == null || request.getRepositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 10;
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(searchJobService.submit(request, limit));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<SearchJob> getSearchJob(@PathVariable String jobId) {
        SearchJob job = searchJobService.get(jobId);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }
}
