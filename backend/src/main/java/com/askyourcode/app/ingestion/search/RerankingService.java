package com.askyourcode.app.ingestion.search;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic local reranking baseline for hybrid candidates. */
@Service
public class RerankingService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_$]+");

    public RerankedSearchResult rerank(String query, HybridSearchResult input, int limit) {
        Set<String> queryTokens = tokens(query);
        String normalizedQuery = normalize(query);
        List<RerankedSearchResult.SearchHit> results = input.getResults().stream()
                .map(hit -> toRerankedHit(hit, queryTokens, normalizedQuery))
                .sorted(Comparator.comparingDouble(RerankedSearchResult.SearchHit::rerankScore)
                        .reversed().thenComparing(RerankedSearchResult.SearchHit::chunkId))
                .limit(limit).toList();
        return new RerankedSearchResult(results, input.getQuery());
    }

    private RerankedSearchResult.SearchHit toRerankedHit(HybridSearchResult.SearchHit hit,
                                                          Set<String> queryTokens,
                                                          String normalizedQuery) {
        String searchableText = String.join(" ", hit.symbolName(), hit.fileName(), hit.content());
        Set<String> candidateTokens = tokens(searchableText);
        long matchingTokens = queryTokens.stream().filter(candidateTokens::contains).count();
        double tokenOverlap = queryTokens.isEmpty() ? 0.0 : (double) matchingTokens / queryTokens.size();
        boolean exactPhrase = normalize(searchableText).contains(normalizedQuery);
        boolean identifierMatch = normalize(hit.symbolName()).equals(normalizedQuery)
                || normalize(hit.fileName()).contains(normalizedQuery);
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

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value != null) {
            TOKEN_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).results()
                    .map(match -> match.group()).forEach(result::add);
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
