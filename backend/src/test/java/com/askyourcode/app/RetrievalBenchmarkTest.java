package com.askyourcode.app;

import com.askyourcode.app.ingestion.search.RetrievalBenchmark;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalBenchmarkTest {
    @Test
    void comparesStrategiesUsingMeanRecallAndReciprocalRank() {
        var cases = List.of(
                new RetrievalBenchmark.BenchmarkCase("JWT validation", Set.of(10L)),
                new RetrievalBenchmark.BenchmarkCase("user lookup", Set.of(20L)));
        Map<String, java.util.function.Function<String, List<Long>>> strategies = new LinkedHashMap<>();
        strategies.put("keyword", query -> query.startsWith("JWT")
                ? List.of(1L, 10L) : List.of(20L, 2L));
        strategies.put("reranked", query -> query.startsWith("JWT")
                ? List.of(10L, 1L) : List.of(20L, 2L));

        var report = RetrievalBenchmark.evaluate(cases, 1, strategies);

        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(report.strategies().get("keyword").meanRecallAtK()).isEqualTo(0.5);
        assertThat(report.strategies().get("reranked").meanRecallAtK()).isEqualTo(1.0);
        assertThat(report.strategies().get("reranked").meanReciprocalRankAtK())
                .isGreaterThan(report.strategies().get("keyword").meanReciprocalRankAtK());
    }
}
