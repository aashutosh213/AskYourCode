package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.EmbeddingEntity;
import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmbeddingRepository extends JpaRepository<EmbeddingEntity, Long> {
    Optional<EmbeddingEntity> findByChunk(CodeChunkEntity chunk);
}
