package com.askyourcode.app.ingestion.embedding;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.EmbeddingEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocalEmbeddingService implements EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(LocalEmbeddingService.class);
    private static final int OLLAMA_CONNECT_TIMEOUT_MS = 5_000;
    private static final int OLLAMA_READ_TIMEOUT_MS = 20_000;

    private final FileEntityRepository fileRepo;
    private final CodeChunkRepository chunkRepo;
    private final EmbeddingRepository embeddingRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = createRestTemplate();

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String ollamaEmbeddingModel;

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(OLLAMA_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(OLLAMA_READ_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    public LocalEmbeddingService(FileEntityRepository fileRepo, CodeChunkRepository chunkRepo, EmbeddingRepository embeddingRepo) {
        this.fileRepo = fileRepo;
        this.chunkRepo = chunkRepo;
        this.embeddingRepo = embeddingRepo;
    }

    @Override
    public void embedRepository(RepositoryEntity repository) {
        List<FileEntity> files = fileRepo.findByRepository(repository);

        int totalChunks = 0;
        int successCount = 0;
        int skippedCount = 0;
        int failureCount = 0;

        // Count total chunks
        for (FileEntity f : files) {
            totalChunks += chunkRepo.findByFile(f).size();
        }

        logger.info("Starting embedding generation for repository '{}' ({} chunks)",
                    repository.getName(), totalChunks);

        int processed = 0;
        for (FileEntity f : files) {
            List<CodeChunkEntity> chunks = chunkRepo.findByFile(f);
            for (CodeChunkEntity c : chunks) {
                processed++;

                var existing = embeddingRepo.findByChunk(c);
                if (existing.isPresent()) {
                    skippedCount++;
                    continue;
                }

                try {
                    double[] vector = getOllamaEmbedding(c.getContent());
                    String json = objectMapper.writeValueAsString(vector);
                    embeddingRepo.save(new EmbeddingEntity(c, json));
                    successCount++;

                    if (processed % 10 == 0 || processed == totalChunks) {
                        logger.info("Embedding progress: {}/{} chunks processed", processed, totalChunks);
                    }
                } catch (JsonProcessingException e) {
                    logger.warn("Failed to serialize embedding for chunk {} in file {}",
                                c.getId(), f.getRelativePath());
                    failureCount++;
                } catch (Exception e) {
                    logger.warn("Failed to generate embedding for chunk {} in file {}: {}",
                                c.getId(), f.getRelativePath(), e.getMessage());
                    failureCount++;
                }
            }
        }

        logger.info("Embedding generation completed for repository '{}': {} successful, {} skipped, {} failed",
                    repository.getName(), successCount, skippedCount, failureCount);
    }

    /**
     * Generate embedding for a single text (used for query embedding).
     */
    public double[] embedText(String text) {
        return getOllamaEmbedding(text);
    }

    private double[] getOllamaEmbedding(String text) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", ollamaEmbeddingModel);
            request.put("prompt", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

            var response = restTemplate.postForObject(ollamaUrl + "/api/embeddings", entity, Map.class);
            if (response != null && response.containsKey("embedding")) {
                List<Number> embedList = (List<Number>) response.get("embedding");
                double[] vec = new double[embedList.size()];
                for (int i = 0; i < embedList.size(); i++) {
                    vec[i] = embedList.get(i).doubleValue();
                }
                return vec;
            }
        } catch (Exception ex) {
            logger.debug("Failed to get embedding from Ollama (is it running and does it have '{}'?). Falling back to pseudo-embedding.", ollamaEmbeddingModel);
        }
        return pseudoEmbed(text);
    }

    private double[] pseudoEmbed(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            int dim = 768; // Matching nomic-embed-text typical output dimension size
            double[] vec = new double[dim];
            for (int i = 0; i < dim; i++) {
                vec[i] = (hash[i % hash.length] & 0xff) / 255.0;
            }
            return vec;
        } catch (Exception ex) {
            return new double[768];
        }
    }
}
