package com.askyourcode.app.ingestion.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Small offline benchmark runner for comparing retrieval strategies against
 * hand-labelled relevant chunk ids.
 */
public final class RetrievalBenchmark {
    private RetrievalBenchmark() {
    }

    public static BenchmarkReport evaluate(List<BenchmarkCase> cases, int k,
                                           Map<String, Function<String, List<Long>>> strategies) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("At least one benchmark case is required");
        }
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one retrieval strategy is required");
        }

        Map<String, StrategyMetrics> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, Function<String, List<Long>>> strategy : strategies.entrySet()) {
            double recall = 0.0;
            double reciprocalRank = 0.0;
            for (BenchmarkCase benchmarkCase : cases) {
                RetrievalMetrics.EvaluationResult result = RetrievalMetrics.evaluate(
                        strategy.getValue().apply(benchmarkCase.query()),
                        benchmarkCase.relevantChunkIds(), k);
                recall += result.recallAtK();
                reciprocalRank += result.reciprocalRankAtK();
            }
            metrics.put(strategy.getKey(), new StrategyMetrics(
                    recall / cases.size(), reciprocalRank / cases.size()));
        }
        return new BenchmarkReport(k, cases.size(), metrics);
    }

    public record BenchmarkCase(String query, Set<Long> relevantChunkIds) {
        public BenchmarkCase {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query is required");
            }
            if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
                throw new IllegalArgumentException("At least one relevant chunk id is required");
            }
        }
    }

    public record StrategyMetrics(double meanRecallAtK, double meanReciprocalRankAtK) {
    }

    public record BenchmarkReport(int k, int caseCount,
                                  Map<String, StrategyMetrics> strategies) {
    }
}
