package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SearchJobService {
    private static final Logger logger = LoggerFactory.getLogger(SearchJobService.class);

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;
    private final TaskExecutor searchTaskExecutor;
    private final Map<String, SearchJob> jobs = new ConcurrentHashMap<>();

    public SearchJobService(HybridSearchService hybridSearchService,
                            RerankingService rerankingService,
                            @Qualifier("searchTaskExecutor") TaskExecutor searchTaskExecutor) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.searchTaskExecutor = searchTaskExecutor;
    }

    public SearchJob submit(VectorSearchRequest request, int limit) {
        String jobId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        SearchJob queued = new SearchJob(jobId, "QUEUED", "Search queued.", null, startedAt, null);
        jobs.put(jobId, queued);
        searchTaskExecutor.execute(() -> runSearch(jobId, request, limit, startedAt));
        return queued;
    }

    public SearchJob get(String jobId) {
        return jobs.get(jobId);
    }

    private void runSearch(String jobId, VectorSearchRequest request, int limit, Instant startedAt) {
        jobs.put(jobId, new SearchJob(jobId, "RUNNING", "Searching repository.", null, startedAt, null));
        try {
            HybridSearchResult hybrid = hybridSearchService.search(
                    request.getQuery(), request.getRepositoryPath(), limit);
            RerankedSearchResult result = rerankingService.rerank(request.getQuery(), hybrid, limit);
            jobs.put(jobId, new SearchJob(jobId, "COMPLETED", "Search completed.", result,
                    startedAt, Instant.now()));
        } catch (Exception ex) {
            logger.error("Search job {} failed", jobId, ex);
            String message = ex.getMessage();
            if (message == null || message.isBlank()) message = ex.getClass().getSimpleName();
            jobs.put(jobId, new SearchJob(jobId, "FAILED", "Search failed: " + message,
                    null, startedAt, Instant.now()));
        }
    }
}
