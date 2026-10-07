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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Qdrant collection '" + collectionName + "'.", e);
        } catch (ExecutionException e) {
            if (hasStatus(e, "NOT_FOUND")) return false;
            throw new IllegalStateException("Unable to check Qdrant collection '" + collectionName + "'.", e);
        }
    }

    private boolean hasStatus(Throwable failure, String code) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(code + ":")) return true;
        }
        return false;
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
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.error("Failed to create collection '{}': {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to ensure collection exists", e);
        }
    }

    /**
     * Delete a collection before rebuilding a repository's vector snapshot.
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
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.error("Failed to delete collection '{}': {}", collectionName, e.getMessage());
            throw new IllegalStateException("Failed to delete Qdrant collection '" + collectionName + "'.", e);
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

        // Determine vector size from first embedding. Corrupt serialized data
        // must fail indexing instead of creating a likely incompatible collection.
        int vectorSize;
        try {
            double[] firstVector = objectMapper.readValue(embeddings.get(0).getVectorJson(), double[].class);
            if (firstVector.length == 0) throw new IllegalStateException("Embedding vector is empty.");
            vectorSize = firstVector.length;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to determine the Qdrant vector size from stored embeddings.", e);
        }

        // Ensure collection exists
        ensureCollection(collectionName, vectorSize);

        // Push in batches
        int total = embeddings.size();
        int successful = 0;
        int failed = 0;
        List<String> failureDetails = new ArrayList<>();

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
                    logger.warn("Failed to create Qdrant point for embedding {} from {}: {}", embedding.getId(),
                            embedding.getChunk().getFile().getRelativePath(), e.getMessage());
                    failed++;
                    if (failureDetails.size() < 5) {
                        failureDetails.add(embedding.getChunk().getFile().getRelativePath() + " (embedding "
                                + embedding.getId() + "): " + safeMessage(e));
                    }
                }
            }

            if (!points.isEmpty()) {
                try {
                    qdrantClient.upsertAsync(collectionName, points).get();
                    successful += points.size();
                    logger.debug("Pushed batch {}/{} ({} points)", (i / BATCH_SIZE) + 1,
                                (total + BATCH_SIZE - 1) / BATCH_SIZE, points.size());
                } catch (InterruptedException | ExecutionException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    logger.error("Failed to upsert batch to collection '{}': {}", collectionName, e.getMessage());
                    failed += points.size();
                    if (failureDetails.size() < 5) {
                        failureDetails.add("Qdrant batch beginning at point " + (i + 1) + ": " + safeMessage(e));
                    }
                }
            }
        }

        logger.info("Completed pushing embeddings to '{}': {} successful, {} failed",
                    collectionName, successful, failed);
        if (failed > 0) {
            String details = String.join("; ", failureDetails);
            String remaining = failed > failureDetails.size()
                    ? "; and additional point failures" : "";
            throw new IllegalStateException("Failed to store " + failed + " embedding point(s) in Qdrant collection '"
                    + collectionName + "': " + details + remaining);
        }
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
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
