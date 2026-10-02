package com.askyourcode.app.ingestion.ask;

public record AskRequest(String query, String repositoryPath, int limit) {
}
