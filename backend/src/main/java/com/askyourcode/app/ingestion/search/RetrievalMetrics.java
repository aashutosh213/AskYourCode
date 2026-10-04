package com.askyourcode.app.ingestion.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline retrieval metrics for a ranked list and a query's expected chunk ids.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    public static EvaluationResult evaluate(List<Long> rankedChunkIds,
                                            Set<Long> relevantChunkIds,
                                            int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
            return new EvaluationResult(k, 0.0, 0.0, 0.0);
        }

        int evaluatedResults = Math.min(k, rankedChunkIds.size());
        Set<Long> retrievedRelevant = new HashSet<>();
        double reciprocalRank = 0.0;
        for (int index = 0; index < evaluatedResults; index++) {
            Long chunkId = rankedChunkIds.get(index);
            if (relevantChunkIds.contains(chunkId)) {
                retrievedRelevant.add(chunkId);
                if (reciprocalRank == 0.0) {
                    reciprocalRank = 1.0 / (index + 1);
                }
            }
        }

        double recallAtK = (double) retrievedRelevant.size() / relevantChunkIds.size();
        double precisionAtK = (double) retrievedRelevant.size() / k;
        return new EvaluationResult(k, recallAtK, precisionAtK, reciprocalRank);
    }

    public record EvaluationResult(int k, double recallAtK, double precisionAtK,
                                   double reciprocalRankAtK) {
    }
}
