package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchService;
import com.askyourcode.app.ingestion.search.HybridSearchResult;
import com.askyourcode.app.ingestion.search.RerankingService;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AskController {
    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;
    private final LocalLlmService localLlmService;
    private final RepositoryEntityRepository repositoryRepository;

    public AskController(HybridSearchService hybridSearchService,
                         RerankingService rerankingService,
                         LocalLlmService localLlmService,
                         RepositoryEntityRepository repositoryRepository) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.localLlmService = localLlmService;
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
        var hybrid = hybridSearchService.search(request.query(), request.repositoryPath(), limit * 2);
        var reranked = rerankingService.rerank(request.query(), hybrid, limit);
        HybridSearchResult retrieval = new HybridSearchResult(
                reranked.getResults().stream().map(hit -> new HybridSearchResult.SearchHit(
                        hit.chunkId(), hit.filePath(), hit.fileName(), hit.symbolName(), hit.symbolType(),
                        hit.content(), hit.startLine(), hit.endLine(), hit.retrievalScore(),
                        hit.keywordMatch(), hit.vectorMatch())).toList(), request.query());
        try {
            return ResponseEntity.ok(localLlmService.answer(request.query(), retrieval));
        } catch (RestClientException | IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Local Ollama chat model is unavailable",
                    "details", "Pull the configured chat model and retry: "
                            + "docker exec -it askyourcode-ollama ollama pull qwen2.5-coder:7b"));
        }
    }
}
