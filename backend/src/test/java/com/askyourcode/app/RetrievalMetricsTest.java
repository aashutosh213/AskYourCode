package com.askyourcode.app;

import com.askyourcode.app.ingestion.search.RetrievalMetrics;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalMetricsTest {

    @Test
    void calculatesRecallAtKAndFirstRelevantRank() {
        var metrics = RetrievalMetrics.evaluate(
                List.of(10L, 42L, 99L, 7L),
                new LinkedHashSet<>(List.of(42L, 7L)),
                3);

        assertThat(metrics.k()).isEqualTo(3);
        assertThat(metrics.recallAtK()).isEqualTo(0.5);
        assertThat(metrics.reciprocalRankAtK()).isEqualTo(0.5);
    }

    @Test
    void returnsZeroWhenNoRelevantChunkIsRetrieved() {
        var metrics = RetrievalMetrics.evaluate(List.of(1L, 2L), Set.of(9L), 2);

        assertThat(metrics.recallAtK()).isZero();
        assertThat(metrics.reciprocalRankAtK()).isZero();
    }

    @Test
    void rejectsInvalidK() {
        assertThatThrownBy(() -> RetrievalMetrics.evaluate(List.of(1L), Set.of(1L), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
