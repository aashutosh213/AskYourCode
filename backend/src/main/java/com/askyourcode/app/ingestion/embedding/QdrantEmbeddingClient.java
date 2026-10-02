package com.askyourcode.app.ingestion.embedding;

import com.askyourcode.app.ingestion.model.EmbeddingEntity;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Component
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true")
public class QdrantEmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(QdrantEmbeddingClient.class);
    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_VECTOR_SIZE = 768;

    private final QdrantClient qdrantClient;
    private final EmbeddingRepository embeddingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QdrantEmbeddingClient(QdrantClient qdrantClient, EmbeddingRepository embeddingRepository) {
        this.qdrantClient = qdrantClient;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * Check if a collection exists in Qdrant.
     */
    public boolean collectionExists(String collectionName) {
        try {
            qdrantClient.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensure collection exists with the correct configuration.
     * Creates the collection if it doesn't exist.
     */
    public void ensureCollection(String collectionName, int vectorSize) {
        try {
            if (collectionExists(collectionName)) {
                logger.debug("Collection '{}' already exists", collectionName);
                return;
            }

            logger.info("Creating Qdrant collection '{}' with vector size {}", collectionName, vectorSize);

            qdrantClient.createCollectionAsync(
                collectionName,
                Collections.VectorParams.newBuilder()
                    .setSize(vectorSize)
                    .setDistance(Collections.Distance.Cosine)
                    .build()
            ).get();

            logger.info("Successfully created collection '{}'", collectionName);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to create collection '{}': {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to ensure collection exists", e);
        }
    }

    /**
     * Delete a collection (primarily for testing).
     */
    public void deleteCollection(String collectionName) {
        try {
            if (!collectionExists(collectionName)) {
                logger.debug("Collection '{}' does not exist, nothing to delete", collectionName);
                return;
            }

            qdrantClient.deleteCollectionAsync(collectionName).get();
            logger.info("Deleted collection '{}'", collectionName);
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Failed to delete collection '{}': {}", collectionName, e.getMessage());
        }
    }

    /**
     * Push all embeddings from the H2 database to Qdrant.
     */
    public void pushAllEmbeddings(String collectionName, String repositoryPath) {
        List<EmbeddingEntity> embeddings = embeddingRepository
                .findByChunk_File_Repository_Path(repositoryPath);
        if (embeddings.isEmpty()) {
            logger.info("No embeddings to push to collection '{}'", collectionName);
            return;
        }

        // Determine vector size from first embedding
        int vectorSize = DEFAULT_VECTOR_SIZE;
        try {
            double[] firstVector = objectMapper.readValue(embeddings.get(0).getVectorJson(), double[].class);
            vectorSize = firstVector.length;
        } catch (Exception e) {
            logger.warn("Could not determine vector size from embeddings, using default: {}", DEFAULT_VECTOR_SIZE);
        }

        // Ensure collection exists
        ensureCollection(collectionName, vectorSize);

        // Push in batches
        int total = embeddings.size();
        int successful = 0;
        int failed = 0;

        logger.info("Pushing {} embeddings to Qdrant collection '{}'", total, collectionName);

        for (int i = 0; i < embeddings.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, embeddings.size());
            List<EmbeddingEntity> batch = embeddings.subList(i, end);

            List<PointStruct> points = new ArrayList<>();
            for (EmbeddingEntity embedding : batch) {
                try {
                    PointStruct point = createPoint(embedding);
                    points.add(point);
                } catch (Exception e) {
                    logger.warn("Failed to create point for embedding {}: {}", embedding.getId(), e.getMessage());
                    failed++;
                }
            }

            if (!points.isEmpty()) {
                try {
                    qdrantClient.upsertAsync(collectionName, points).get();
                    successful += points.size();
                    logger.debug("Pushed batch {}/{} ({} points)", (i / BATCH_SIZE) + 1,
                                (total + BATCH_SIZE - 1) / BATCH_SIZE, points.size());
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to upsert batch to collection '{}': {}", collectionName, e.getMessage());
                    failed += points.size();
                }
            }
        }

        logger.info("Completed pushing embeddings to '{}': {} successful, {} failed",
                    collectionName, successful, failed);
    }

    /**
     * Create a Qdrant point from an embedding entity.
     */
    private PointStruct createPoint(EmbeddingEntity embedding) throws Exception {
        double[] vector = objectMapper.readValue(embedding.getVectorJson(), double[].class);

        var chunk = embedding.getChunk();
        Map<String, JsonWithInt.Value> payload = new HashMap<>();
        payload.put("chunkId", value(chunk.getId()));
        payload.put("filePath", value(chunk.getFile().getRelativePath()));
        payload.put("fileName", value(chunk.getFile().getFileName()));
        payload.put("symbolName", value(chunk.getSymbolName()));
        payload.put("symbolType", value(chunk.getSymbolType()));
        payload.put("content", value(chunk.getContent()));
        payload.put("startLine", value(chunk.getStartLine()));
        payload.put("endLine", value(chunk.getEndLine()));

        float[] floatVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            floatVector[i] = (float) vector[i];
        }

        return PointStruct.newBuilder()
            // Search results use chunk ids to join vector candidates with
            // BM25 candidates during hybrid retrieval.
            .setId(id(chunk.getId()))
            .setVectors(vectors(floatVector))
            .putAllPayload(payload)
            .build();
    }
}
