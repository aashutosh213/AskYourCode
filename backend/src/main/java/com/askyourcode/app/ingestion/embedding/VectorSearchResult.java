package com.askyourcode.app.ingestion.embedding;

import java.util.List;

public class VectorSearchResult {
    private final List<SearchHit> results;
    private final String query;
    private final int resultsCount;

    public VectorSearchResult(List<SearchHit> results, String query, int resultsCount) {
        this.results = results;
        this.query = query;
        this.resultsCount = resultsCount;
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

    public static class SearchHit {
        private final Long chunkId;
        private final String filePath;
        private final String fileName;
        private final String symbolName;
        private final String symbolType;
        private final String content;
        private final int startLine;
        private final int endLine;
        private final float score;

        public SearchHit(Long chunkId, String filePath, String fileName, String symbolName,
                         String symbolType, String content, int startLine, int endLine, float score) {
            this.chunkId = chunkId;
            this.filePath = filePath;
            this.fileName = fileName;
            this.symbolName = symbolName;
            this.symbolType = symbolType;
            this.content = content;
            this.startLine = startLine;
            this.endLine = endLine;
            this.score = score;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public String getSymbolName() {
            return symbolName;
        }

        public String getSymbolType() {
            return symbolType;
        }

        public String getContent() {
            return content;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public float getScore() {
            return score;
        }
    }
}