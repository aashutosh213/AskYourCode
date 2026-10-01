package com.askyourcode.app.ingestion;

import java.util.List;

public record RepositoryIndexResponse(
        String repositoryPath,
        String status,
        String message,
        String jobId,
        List<RepositoryFileMetadata> files
) {
}
