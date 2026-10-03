package com.askyourcode.app.ingestion.search;

import java.time.Instant;

public record SearchJob(String jobId, String status, String message,
                        RerankedSearchResult result, Instant startedAt,
                        Instant completedAt) {
}
