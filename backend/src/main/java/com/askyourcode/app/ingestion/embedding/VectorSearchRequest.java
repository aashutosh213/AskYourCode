package com.askyourcode.app.ingestion.embedding;

public class VectorSearchRequest {
    private String query;
    private String repositoryPath;
    private int limit = 10;

    public VectorSearchRequest() {
    }

    public VectorSearchRequest(String query, String repositoryPath, int limit) {
        this.query = query;
        this.repositoryPath = repositoryPath;
        this.limit = limit;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}