package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, Long> {
    Optional<RepositoryEntity> findByPath(String path);
}
