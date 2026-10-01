package com.askyourcode.app.ingestion.embedding;

import com.askyourcode.app.ingestion.model.RepositoryEntity;

public interface EmbeddingService {
    /**
     * Create embeddings for all chunks belonging to the repository.
     */
    void embedRepository(RepositoryEntity repository);
}
