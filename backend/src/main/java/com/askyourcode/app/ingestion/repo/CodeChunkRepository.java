package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, Long> {
    List<CodeChunkEntity> findByFile(FileEntity file);
    Page<CodeChunkEntity> findByFile(FileEntity file, Pageable pageable);

    @Query("select c from CodeChunkEntity c join fetch c.file f where f.repository = :repository")
    List<CodeChunkEntity> findAllByRepositoryWithFile(@Param("repository") com.askyourcode.app.ingestion.model.RepositoryEntity repository);
}
