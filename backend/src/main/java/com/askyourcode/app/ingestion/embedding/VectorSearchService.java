package com.askyourcode.app.ingestion.embedding;

import com.askyourcode.app.ingestion.RepositoryScanner;
import com.askyourcode.app.ingestion.model.EmbeddingEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);
    private static final long QDRANT_SEARCH_TIMEOUT_SECONDS = 30;

    private final QdrantClient qdrantClient;
    private final LocalEmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final RepositoryEntityRepository repositoryRepository;

    public VectorSearchService(ObjectProvider<QdrantClient> qdrantClientProvider,
                               LocalEmbeddingService embeddingService,
                               EmbeddingRepository embeddingRepository,
                               RepositoryEntityRepository repositoryRepository) {
        this.qdrantClient = qdrantClientProvider.getIfAvailable();
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.repositoryRepository = repositoryRepository;
    }

    /**
     * Search for code chunks similar to the query text.
     */
    public VectorSearchResult search(String query, String repositoryPath, int limit) {
        if (qdrantClient == null) {
            logger.warn("Qdrant client not available (qdrant.enabled=false)");
            return new VectorSearchResult(Collections.emptyList(), query, 0,
                    "Qdrant vector search is disabled; only keyword retrieval is available.");
        }

        // Find repository by path
        var repoOpt = repositoryRepository.findByPath(repositoryPath);
        if (repoOpt.isEmpty()) {
            logger.warn("Repository not found: {}", repositoryPath);
            return new VectorSearchResult(Collections.emptyList(), query, 0);
        }

        RepositoryEntity repository = repoOpt.get();
        String collectionName = QdrantCollectionNames.forRepositoryPath(repository.getPath());

        // Generate embedding for query
        double[] queryVector;
        try {
            queryVector = embeddingService.embedText(query);
        } catch (RuntimeException ex) {
            String warning = ex.getMessage() == null ? "Local query embedding is unavailable." : ex.getMessage();
            logger.warn("Vector retrieval unavailable: {}", warning);
            return new VectorSearchResult(Collections.emptyList(), query, 0, warning);
        }
        if (queryVector == null || queryVector.length == 0) {
            logger.warn("Failed to generate query embedding");
            return new VectorSearchResult(Collections.emptyList(), query, 0);
        }

        // Search Qdrant
        try {
            List<Float> floatQueryVector = new ArrayList<>(queryVector.length);
            for (double value : queryVector) {
                floatQueryVector.add((float) value);
            }

            var searchRequest = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(floatQueryVector)
                // Generated artifacts may already exist in older collections;
                // over-fetch so filtering them still leaves useful source hits.
                .setLimit(Math.min(limit * 5, 100))
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                // Reranking only needs the payload; returning every stored vector
                // makes the Qdrant response unnecessarily large.
                .setWithVectors(WithVectorsSelectorFactory.enable(false))
                .build();

            var searchPoints = qdrantClient.searchAsync(searchRequest)
                    .get(QDRANT_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<VectorSearchResult.SearchHit> results = new ArrayList<>();
            for (var point : searchPoints) {
                var payload = point.getPayloadMap();
                String filePath = getStringValue(payload, "filePath");
                if (RepositoryScanner.isIgnoredRelativePath(filePath)) continue;
                VectorSearchResult.SearchHit hit = new VectorSearchResult.SearchHit(
                    point.getId().getNum(),
                    filePath,
                    getStringValue(payload, "fileName"),
                    getStringValue(payload, "symbolName"),
                    getStringValue(payload, "symbolType"),
                    getStringValue(payload, "content"),
                    getIntValue(payload, "startLine"),
                    getIntValue(payload, "endLine"),
                    point.getScore()
                );
                results.add(hit);
                if (results.size() >= limit) break;
            }

            logger.info("Vector search for '{}' returned {} results", query, results.size());
            return new VectorSearchResult(results, query, results.size());

        } catch (Exception e) {
            logger.error("Vector search failed for query '{}': {}", query, e.getMessage());
            return new VectorSearchResult(Collections.emptyList(), query, 0,
                    "Qdrant vector search failed; hybrid retrieval can still use keyword matches.");
        }
    }

    private String getStringValue(Map<String, JsonWithInt.Value> payload, String key) {
        var value = payload.get(key);
        return value != null ? value.getStringValue() : "";
    }

    private int getIntValue(Map<String, JsonWithInt.Value> payload, String key) {
        var value = payload.get(key);
        if (value != null && value.getKindCase() == JsonWithInt.Value.KindCase.INTEGER_VALUE) {
            return (int) value.getIntegerValue();
        }
        return 0;
    }
}
