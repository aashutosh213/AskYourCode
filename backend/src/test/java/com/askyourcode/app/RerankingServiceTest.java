package com.askyourcode.app;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import com.askyourcode.app.ingestion.search.RerankingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RerankingServiceTest {
    @Test
    void promotesAnExactIdentifierMatchOverAWeakerLexicalCandidate() {
        var exact = new HybridSearchResult.SearchHit(2L, "src/AuthService.java", "AuthService.java",
                "validateToken", "method", "return JwtAuthenticationFilter.isValid(token);", 10, 12,
                1.0 / 61.0, false, true);
        var weaker = new HybridSearchResult.SearchHit(1L, "src/TokenService.java", "TokenService.java",
                "issueToken", "method", "return token;", 20, 22, 2.0 / 61.0, true, false);

        var result = new RerankingService().rerank("validateToken",
                new HybridSearchResult(List.of(weaker, exact), "validateToken"), 2);

        assertThat(result.getResults()).extracting("chunkId").containsExactly(2L, 1L);
        assertThat(result.getResults().getFirst().rerankScore())
                .isGreaterThan(result.getResults().get(1).rerankScore());
    }

    @Test
    void questionWordsDoNotDecideTheRankingOfANaturalLanguageQuery() {
        var named = new HybridSearchResult.SearchHit(2L, "src/RepositoryScanner.java", "RepositoryScanner.java",
                "isIgnoredRelativePath", "method", "return IGNORED_DIRECTORIES.contains(segment);", 10, 12,
                1.0 / 61.0, false, true);
        var unrelated = new HybridSearchResult.SearchHit(1L, "src/Runner.java", "Runner.java",
                "run", "method", "public void run() { log(\"start\"); }", 20, 22, 2.0 / 61.0, true, false);

        var result = new RerankingService().rerank("How does the repository scanning work?",
                new HybridSearchResult(List.of(unrelated, named), "How does the repository scanning work?"), 2);

        assertThat(result.getResults()).extracting("chunkId").containsExactly(2L, 1L);
    }
}
