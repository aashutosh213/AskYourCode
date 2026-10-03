package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import com.askyourcode.app.ingestion.search.HybridSearchService;
import com.askyourcode.app.ingestion.search.RerankingService;
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
public class AskJobService {
    private static final Logger logger = LoggerFactory.getLogger(AskJobService.class);

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;
    private final LocalLlmService localLlmService;
    private final TaskExecutor askTaskExecutor;
    private final Map<String, AskJob> jobs = new ConcurrentHashMap<>();

    public AskJobService(HybridSearchService hybridSearchService,
                         RerankingService rerankingService,
                         LocalLlmService localLlmService,
                         @Qualifier("askTaskExecutor") TaskExecutor askTaskExecutor) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.localLlmService = localLlmService;
        this.askTaskExecutor = askTaskExecutor;
    }

    public AskJob submit(AskRequest request, int limit) {
        String jobId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        AskJob queued = new AskJob(jobId, "QUEUED", "Question queued.", null, startedAt, null);
        jobs.put(jobId, queued);
        askTaskExecutor.execute(() -> runAsk(jobId, request, limit, startedAt));
        return queued;
    }

    public AskJob get(String jobId) {
        return jobs.get(jobId);
    }

    private void runAsk(String jobId, AskRequest request, int limit, Instant startedAt) {
        jobs.put(jobId, new AskJob(jobId, "RUNNING", "Retrieving code and asking the local model.",
                null, startedAt, null));
        try {
            var hybrid = hybridSearchService.search(request.query(), request.repositoryPath(), limit * 2);
            var reranked = rerankingService.rerank(request.query(), hybrid, limit);
            HybridSearchResult retrieval = new HybridSearchResult(
                    reranked.getResults().stream().map(hit -> new HybridSearchResult.SearchHit(
                            hit.chunkId(), hit.filePath(), hit.fileName(), hit.symbolName(), hit.symbolType(),
                            hit.content(), hit.startLine(), hit.endLine(), hit.retrievalScore(),
                            hit.keywordMatch(), hit.vectorMatch())).toList(), request.query());
            AskResponse result = localLlmService.answer(request.query(), retrieval);
            jobs.put(jobId, new AskJob(jobId, "COMPLETED", "Answer ready.", result,
                    startedAt, Instant.now()));
        } catch (Exception ex) {
            logger.error("Ask job {} failed", jobId, ex);
            String message = ex.getMessage();
            if (message == null || message.isBlank()) message = ex.getClass().getSimpleName();
            jobs.put(jobId, new AskJob(jobId, "FAILED", message, null, startedAt, Instant.now()));
        }
    }
}
