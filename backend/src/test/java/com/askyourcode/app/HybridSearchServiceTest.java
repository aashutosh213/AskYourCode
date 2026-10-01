package com.askyourcode.app;

import com.askyourcode.app.ingestion.embedding.VectorSearchResult;
import com.askyourcode.app.ingestion.embedding.VectorSearchService;
import com.askyourcode.app.ingestion.search.HybridSearchService;
import com.askyourcode.app.ingestion.search.KeywordSearchResult;
import com.askyourcode.app.ingestion.search.KeywordSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridSearchServiceTest {

    @Test
    void givesAChunkFromBothRetrieversTheCombinedRrfScore() {
        KeywordSearchService keywordService = mock(KeywordSearchService.class);
        VectorSearchService vectorService = mock(VectorSearchService.class);
        KeywordSearchResult.SearchHit keywordHit = new KeywordSearchResult.SearchHit(
                7L, "AuthService.java", "AuthService.java", "validateToken", "method",
                "return JwtAuthenticationFilter.isValid(token);", 10, 12, 4.2f);
        VectorSearchResult.SearchHit vectorHit = new VectorSearchResult.SearchHit(
                7L, "AuthService.java", "AuthService.java", "validateToken", "method",
                "return JwtAuthenticationFilter.isValid(token);", 10, 12, 0.91f);
        when(keywordService.search("JWT", "/repo", 10))
                .thenReturn(new KeywordSearchResult(List.of(keywordHit), "JWT"));
        when(vectorService.search("JWT", "/repo", 10))
                .thenReturn(new VectorSearchResult(List.of(vectorHit), "JWT", 1));

        var result = new HybridSearchService(keywordService, vectorService).search("JWT", "/repo", 5);

        assertThat(result.getResults()).hasSize(1);
        var hit = result.getResults().getFirst();
        assertThat(hit.chunkId()).isEqualTo(7L);
        assertThat(hit.keywordMatch()).isTrue();
        assertThat(hit.vectorMatch()).isTrue();
        assertThat(hit.score()).isEqualTo(2.0 / 61.0);
    }
}
