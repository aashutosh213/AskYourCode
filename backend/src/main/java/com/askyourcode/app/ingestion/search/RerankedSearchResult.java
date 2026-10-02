package com.askyourcode.app.ingestion.search;

import java.util.List;

public class RerankedSearchResult {
    private final List<SearchHit> results;
    private final String query;
    private final int resultsCount;

    public RerankedSearchResult(List<SearchHit> results, String query) {
        this.results = results;
        this.query = query;
        this.resultsCount = results.size();
    }

    public List<SearchHit> getResults() { return results; }
    public String getQuery() { return query; }
    public int getResultsCount() { return resultsCount; }

    public record SearchHit(
            Long chunkId, String filePath, String fileName, String symbolName,
            String symbolType, String content, int startLine, int endLine,
            double retrievalScore, double rerankScore,
            boolean keywordMatch, boolean vectorMatch) {
    }
}
