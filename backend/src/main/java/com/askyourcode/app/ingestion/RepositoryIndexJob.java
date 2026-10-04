package com.askyourcode.app.ingestion;

import java.time.Instant;
import java.util.List;

public record RepositoryIndexJob(
        String jobId,
        String repositoryPath,
        String status,
        String stage,
        int filesDiscovered,
        List<RepositoryFileMetadata> files,
        Instant startedAt,
        Instant completedAt,
        String message
) {
}
