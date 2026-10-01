package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.embedding.VectorSearchResult;
import com.askyourcode.app.ingestion.embedding.VectorSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridSearchService {

    private static final int RRF_K = 60;

    private final KeywordSearchService keywordSearchService;
    private final VectorSearchService vectorSearchService;

    public HybridSearchService(KeywordSearchService keywordSearchService,
                               VectorSearchService vectorSearchService) {
        this.keywordSearchService = keywordSearchService;
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * Runs both retrievers independently and combines their rank positions using
     * Reciprocal Rank Fusion: score = sum(1 / (k + rank)).
     */
    public HybridSearchResult search(String query, String repositoryPath, int limit) {
        int candidateLimit = Math.min(Math.max(limit * 2, 10), 100);
        KeywordSearchResult keyword = keywordSearchService.search(query, repositoryPath, candidateLimit);
        VectorSearchResult vector = vectorSearchService.search(query, repositoryPath, candidateLimit);

        Map<Long, Candidate> candidates = new HashMap<>();
        for (int i = 0; i < keyword.getResults().size(); i++) {
            KeywordSearchResult.SearchHit hit = keyword.getResults().get(i);
            Candidate candidate = candidates.computeIfAbsent(hit.chunkId(), id -> Candidate.from(hit));
            candidate.score += reciprocalRank(i + 1);
            candidate.keywordMatch = true;
        }
        for (int i = 0; i < vector.getResults().size(); i++) {
            VectorSearchResult.SearchHit hit = vector.getResults().get(i);
            Candidate candidate = candidates.computeIfAbsent(hit.getChunkId(), id -> Candidate.from(hit));
            candidate.score += reciprocalRank(i + 1);
            candidate.vectorMatch = true;
        }

        List<HybridSearchResult.SearchHit> results = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed()
                        .thenComparing(candidate -> candidate.chunkId))
                .limit(limit)
                .map(Candidate::toResult)
                .toList();
        return new HybridSearchResult(results, query);
    }

    private double reciprocalRank(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    private static final class Candidate {
        private final Long chunkId;
        private final String filePath;
        private final String fileName;
        private final String symbolName;
        private final String symbolType;
        private final String content;
        private final int startLine;
        private final int endLine;
        private double score;
        private boolean keywordMatch;
        private boolean vectorMatch;

        private Candidate(Long chunkId, String filePath, String fileName, String symbolName,
                          String symbolType, String content, int startLine, int endLine) {
            this.chunkId = chunkId;
            this.filePath = filePath;
            this.fileName = fileName;
            this.symbolName = symbolName;
            this.symbolType = symbolType;
            this.content = content;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        private static Candidate from(KeywordSearchResult.SearchHit hit) {
            return new Candidate(hit.chunkId(), hit.filePath(), hit.fileName(), hit.symbolName(),
                    hit.symbolType(), hit.content(), hit.startLine(), hit.endLine());
        }

        private static Candidate from(VectorSearchResult.SearchHit hit) {
            return new Candidate(hit.getChunkId(), hit.getFilePath(), hit.getFileName(), hit.getSymbolName(),
                    hit.getSymbolType(), hit.getContent(), hit.getStartLine(), hit.getEndLine());
        }

        private HybridSearchResult.SearchHit toResult() {
            return new HybridSearchResult.SearchHit(chunkId, filePath, fileName, symbolName, symbolType,
                    content, startLine, endLine, score, keywordMatch, vectorMatch);
        }
    }
}
