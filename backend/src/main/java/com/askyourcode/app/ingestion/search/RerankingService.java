package com.askyourcode.app.ingestion.search;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Deterministic local reranking baseline for hybrid candidates. */
@Service
public class RerankingService {
    public RerankedSearchResult rerank(String query, HybridSearchResult input, int limit) {
        Set<String> queryTokens = CodeSearchText.tokens(query);
        String normalizedQuery = CodeSearchText.normalize(query);
        List<RerankedSearchResult.SearchHit> results = input.getResults().stream()
                .map(hit -> toRerankedHit(hit, queryTokens, normalizedQuery))
                .sorted(Comparator.comparingDouble(RerankedSearchResult.SearchHit::rerankScore)
                        .reversed().thenComparing(RerankedSearchResult.SearchHit::chunkId))
                .limit(limit).toList();
        return new RerankedSearchResult(results, input.getQuery(), input.getWarning());
    }

    private RerankedSearchResult.SearchHit toRerankedHit(HybridSearchResult.SearchHit hit,
                                                          Set<String> queryTokens,
                                                          String normalizedQuery) {
        String searchableText = String.join(" ", hit.symbolName(), hit.fileName(), hit.content());
        Set<String> candidateTokens = CodeSearchText.tokens(searchableText);
        long matchingTokens = queryTokens.stream().filter(candidateTokens::contains).count();
        double tokenOverlap = queryTokens.isEmpty() ? 0.0 : (double) matchingTokens / queryTokens.size();
        boolean exactPhrase = CodeSearchText.normalize(searchableText).contains(normalizedQuery);
        boolean identifierMatch = CodeSearchText.normalize(hit.symbolName()).equals(normalizedQuery)
                || CodeSearchText.normalize(hit.fileName()).contains(normalizedQuery);
        double rerankScore = (0.45 * tokenOverlap)
                + (0.35 * (exactPhrase ? 1.0 : 0.0))
                + (0.15 * (identifierMatch ? 1.0 : 0.0))
                + (0.05 * normalizedRetrievalScore(hit.score()));
        return new RerankedSearchResult.SearchHit(hit.chunkId(), hit.filePath(), hit.fileName(),
                hit.symbolName(), hit.symbolType(), hit.content(), hit.startLine(), hit.endLine(),
                hit.score(), rerankScore, hit.keywordMatch(), hit.vectorMatch());
    }

    private double normalizedRetrievalScore(double score) {
        return Math.min(1.0, score * 61.0 / 2.0);
    }

}
