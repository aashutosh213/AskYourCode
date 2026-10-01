package com.askyourcode.app.ingestion.repo;

import com.askyourcode.app.ingestion.model.IndexJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexJobRepository extends JpaRepository<IndexJobEntity, String> {
}
