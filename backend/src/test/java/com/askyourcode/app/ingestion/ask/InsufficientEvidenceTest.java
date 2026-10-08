package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InsufficientEvidenceTest {

    @Test
    void returnsInsufficientEvidenceWithoutCallingOllamaWhenContextIsEmpty() {
        var service = new RecordingLocalLlmService();
        var retrieval = new HybridSearchResult(List.of(), "where is authentication?");

        AskResponse response = service.answer(retrieval.getQuery(), retrieval);

        assertThat(service.ollamaCalled).isFalse();
        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(response.answer()).contains("couldn't find enough usable code evidence");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void labelsCitationReferencesOutsideSelectedEvidenceAsUnverified() {
        var citations = List.of(new AskResponse.Citation(1, "src/Auth.java", "validate", 1, 3));

        String answer = LocalLlmService.retainSelectedCitations(
                "Validation is here [1], unrelated source [2], and invalid zero [0].", citations);

        assertThat(answer).contains("here [1]")
                .contains("unrelated source [unverified citation]")
                .contains("invalid zero [unverified citation]");
    }

    @Test
    void keepsAWeakButUsableCandidateForTheModelToAssess() {
        var service = new RecordingLocalLlmService();
        var hit = new HybridSearchResult.SearchHit(3L, "src/Other.java", "Other.java", "unrelated",
                "method", "unrelated code", 1, 2, 0.001, true, false);
        var retrieval = new HybridSearchResult(List.of(hit), "where is authentication?");

        AskResponse response = service.answer(retrieval.getQuery(), retrieval);

        assertThat(service.ollamaCalled).isTrue();
        assertThat(response.answer()).contains("context is insufficient");
    }

    private static class RecordingLocalLlmService extends LocalLlmService {
        private boolean ollamaCalled;

        @Override
        Map<?, ?> callOllamaWithRetry(Map<String, Object> request, HttpHeaders headers) {
            ollamaCalled = true;
            return Map.of("message", Map.of("content", "The context is insufficient [9]."));
        }
    }
}
