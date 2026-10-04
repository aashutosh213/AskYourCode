package com.askyourcode.app.ingestion.search;

import java.util.List;

public class HybridSearchResult {
    private final List<SearchHit> results;
    private final String query;
    private final int resultsCount;
    private final String warning;

    public HybridSearchResult(List<SearchHit> results, String query) {
        this(results, query, null);
    }

    public HybridSearchResult(List<SearchHit> results, String query, String warning) {
        this.results = results;
        this.query = query;
        this.resultsCount = results.size();
        this.warning = warning;
    }

    public List<SearchHit> getResults() {
        return results;
    }

    public String getQuery() {
        return query;
    }

    public int getResultsCount() {
        return resultsCount;
    }

    public String getWarning() { return warning; }

    public record SearchHit(
            Long chunkId,
            String filePath,
            String fileName,
            String symbolName,
            String symbolType,
            String content,
            int startLine,
            int endLine,
            double score,
            boolean keywordMatch,
            boolean vectorMatch) {
    }
}
