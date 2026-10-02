package com.askyourcode.app;

import com.askyourcode.app.ingestion.ask.LocalLlmService;
import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLlmServiceTest {
    @Test
    void buildsNumberedCitationContextFromRetrievedChunks() {
        var retrieval = new HybridSearchResult(List.of(new HybridSearchResult.SearchHit(
                7L, "src/AuthService.java", "AuthService.java", "validateToken", "method",
                "return JwtAuthenticationFilter.isValid(token);", 10, 12,
                0.2, true, true)), "Where is JWT validation?");

        var service = new LocalLlmService();

        assertThat(service.buildContext(retrieval))
                .contains("[1] src/AuthService.java:10-12 symbol=validateToken")
                .contains("JwtAuthenticationFilter.isValid(token)");
    }
}
