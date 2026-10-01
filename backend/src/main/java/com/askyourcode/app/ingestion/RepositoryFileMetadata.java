package com.askyourcode.app.ingestion;

public record RepositoryFileMetadata(
        String relativePath,
        String fileName,
        String language,
        long sizeBytes
) {
}
