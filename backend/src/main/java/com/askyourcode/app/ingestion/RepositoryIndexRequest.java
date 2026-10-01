package com.askyourcode.app.ingestion;

import jakarta.validation.constraints.NotBlank;

public record RepositoryIndexRequest(
        @NotBlank(message = "repositoryPath is required") String repositoryPath
) {
}
