package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AskController {
    private final AskJobService askJobService;
    private final RepositoryEntityRepository repositoryRepository;

    public AskController(AskJobService askJobService,
                         RepositoryEntityRepository repositoryRepository) {
        this.askJobService = askJobService;
        this.repositoryRepository = repositoryRepository;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()
                || request.repositoryPath() == null || request.repositoryPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (repositoryRepository.findByPath(request.repositoryPath()).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Repository is not indexed",
                    "repositoryPath", request.repositoryPath(),
                    "details", "Index this exact path with POST /api/repositories/index first"));
        }
        int limit = request.limit() > 0 ? Math.min(request.limit(), 10) : 5;
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(askJobService.submit(request, limit));
    }

    @org.springframework.web.bind.annotation.GetMapping("/ask/jobs/{jobId}")
    public ResponseEntity<AskJob> getAskJob(@org.springframework.web.bind.annotation.PathVariable String jobId) {
        AskJob job = askJobService.get(jobId);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }
}
