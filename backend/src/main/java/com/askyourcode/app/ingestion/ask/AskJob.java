package com.askyourcode.app.ingestion.ask;

import java.time.Instant;

public record AskJob(String jobId, String status, String message, AskResponse result,
                     Instant startedAt, Instant completedAt) {
}
