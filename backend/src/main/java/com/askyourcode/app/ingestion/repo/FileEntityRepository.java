package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByRepository(RepositoryEntity repository);
    java.util.Optional<FileEntity> findByRepositoryAndRelativePath(RepositoryEntity repository, String relativePath);
}
