package com.askyourcode.app.ingestion.search;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Deterministic local reranking baseline for hybrid candidates. */
@Service
public class RerankingService {
    public RerankedSearchResult rerank(String query, HybridSearchResult input, int limit) {
        // Question words such as "how" or "where" are excluded so they neither
        // inflate nor dilute the overlap with the identifiers being searched for.
        Set<String> queryTokens = CodeSearchText.queryTerms(query);
        String normalizedQuery = String.join(" ", queryTokens);
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
        boolean exactPhrase = !normalizedQuery.isEmpty()
                && CodeSearchText.normalize(searchableText).contains(normalizedQuery);
        // Graded rather than all-or-nothing: a natural-language query names some
        // identifiers ("repository", "scanner") and may not name the whole symbol.
        Set<String> identifierTokens = CodeSearchText.tokens(hit.symbolName() + " " + hit.fileName());
        long matchingIdentifiers = queryTokens.stream().filter(identifierTokens::contains).count();
        double identifierOverlap = queryTokens.isEmpty() ? 0.0 : (double) matchingIdentifiers / queryTokens.size();
        double rerankScore = (0.45 * tokenOverlap)
                + (0.35 * (exactPhrase ? 1.0 : 0.0))
                + (0.15 * identifierOverlap)
                + (0.05 * normalizedRetrievalScore(hit.score()));
        return new RerankedSearchResult.SearchHit(hit.chunkId(), hit.filePath(), hit.fileName(),
                hit.symbolName(), hit.symbolType(), hit.content(), hit.startLine(), hit.endLine(),
                hit.score(), rerankScore, hit.keywordMatch(), hit.vectorMatch());
    }

    private double normalizedRetrievalScore(double score) {
        return Math.min(1.0, score * 61.0 / 2.0);
    }

}
