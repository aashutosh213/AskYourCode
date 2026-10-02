package com.askyourcode.app.ingestion.embedding;

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

@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

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
            return new VectorSearchResult(Collections.emptyList(), query, 0);
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
        double[] queryVector = embeddingService.embedText(query);
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
                .setLimit(limit)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .setWithVectors(WithVectorsSelectorFactory.enable(true))
                .build();

            var searchPoints = qdrantClient.searchAsync(searchRequest).get();

            List<VectorSearchResult.SearchHit> results = new ArrayList<>();
            for (var point : searchPoints) {
                var payload = point.getPayloadMap();
                VectorSearchResult.SearchHit hit = new VectorSearchResult.SearchHit(
                    point.getId().getNum(),
                    getStringValue(payload, "filePath"),
                    getStringValue(payload, "fileName"),
                    getStringValue(payload, "symbolName"),
                    getStringValue(payload, "symbolType"),
                    getStringValue(payload, "content"),
                    getIntValue(payload, "startLine"),
                    getIntValue(payload, "endLine"),
                    point.getScore()
                );
                results.add(hit);
            }

            logger.info("Vector search for '{}' returned {} results", query, results.size());
            return new VectorSearchResult(results, query, results.size());

        } catch (Exception e) {
            logger.error("Vector search failed for query '{}': {}", query, e.getMessage());
            return new VectorSearchResult(Collections.emptyList(), query, 0);
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
