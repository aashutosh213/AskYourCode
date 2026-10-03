package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.IndexJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IndexJobRepository extends JpaRepository<IndexJobEntity, String> {
    Optional<IndexJobEntity> findTopByRepositoryPathAndStatusOrderByCompletedAtDesc(
            String repositoryPath, String status);
}
