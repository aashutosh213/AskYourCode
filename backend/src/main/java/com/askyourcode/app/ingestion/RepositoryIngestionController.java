package com.askyourcode.app.ingestion;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RepositoryIngestionController {

    private final RepositoryIndexingService repositoryIndexingService;

    public RepositoryIngestionController(RepositoryIndexingService repositoryIndexingService) {
        this.repositoryIndexingService = repositoryIndexingService;
    }

    @PostMapping("/repositories/index")
    public ResponseEntity<RepositoryIndexResponse> indexRepository(@Valid @RequestBody RepositoryIndexRequest request) {
        RepositoryIndexResponse response = repositoryIndexingService.queueRepositoryIndex(request.repositoryPath());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
