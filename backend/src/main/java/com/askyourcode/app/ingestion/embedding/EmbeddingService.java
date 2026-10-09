package com.askyourcode.app.ingestion.embedding;

import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;

import java.util.List;

public interface EmbeddingService {
    /**
     * Create or refresh embeddings for all chunks belonging to the repository.
     *
     * @return files that received new vectors and therefore need to be pushed to the vector store
     */
    List<FileEntity> embedRepository(RepositoryEntity repository);
}
