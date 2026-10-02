package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.EmbeddingEntity;
import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface EmbeddingRepository extends JpaRepository<EmbeddingEntity, Long> {
    Optional<EmbeddingEntity> findByChunk(CodeChunkEntity chunk);

    List<EmbeddingEntity> findByChunk_File_Repository_Path(String repositoryPath);
}
