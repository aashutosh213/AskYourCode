package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AskContextBuilderTest {

    @Test
    void skipsOversizedChunkAndNumbersOnlySelectedEvidence() {
        var retrieval = new HybridSearchResult(List.of(
                hit(1L, "src/Large.java", "x".repeat(500)),
                hit(2L, "src/Small.java", "small relevant method")), "query");

        var result = new AskContextBuilder(200).build(retrieval);

        assertThat(result.selectedHits()).extracting(HybridSearchResult.SearchHit::chunkId).containsExactly(2L);
        assertThat(result.text()).startsWith("[1] src/Small.java").contains("small relevant method");
        assertThat(result.text()).doesNotContain("Large.java");
        assertThat(result.citations()).containsExactly(
                new AskResponse.Citation(1, "src/Small.java", "lookup", 4, 8));
    }

    @Test
    void removesDuplicateChunkIdsWithoutDuplicatingCitation() {
        var original = hit(7L, "src/Auth.java", "validate token");
        var repeated = hit(7L, "src/Auth.java", "validate token");
        var result = new AskContextBuilder(2_000)
                .build(new HybridSearchResult(List.of(original, repeated), "query"));

        assertThat(result.selectedHits()).containsExactly(original);
        assertThat(result.citations()).hasSize(1);
        assertThat(result.text()).containsOnlyOnce("validate token");
    }

    @Test
    void returnsEmptyContextWhenThereAreNoUsableHits() {
        var result = new AskContextBuilder(2_000).build(
                new HybridSearchResult(List.of(hit(1L, "src/Empty.java", "  ")), "query"));

        assertThat(result.text()).isEmpty();
        assertThat(result.selectedHits()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    private HybridSearchResult.SearchHit hit(Long id, String path, String content) {
        return new HybridSearchResult.SearchHit(id, path, path.substring(path.lastIndexOf('/') + 1),
                "lookup", "method", content, 4, 8, 0.1, true, false);
    }
}
