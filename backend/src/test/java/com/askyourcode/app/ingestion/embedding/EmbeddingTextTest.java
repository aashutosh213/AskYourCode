package com.askyourcode.app.ingestion.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextTest {

    @Test
    void documentTextCarriesPrefixPathAndQualifiedSymbolBeforeCode() {
        String text = EmbeddingText.forChunk("src/auth/AuthService.java", "validateToken", "method",
                "AuthService", "public boolean validateToken() { return true; }\n");

        assertThat(text).isEqualTo("search_document: File: src/auth/AuthService.java\n"
                + "Symbol: AuthService.validateToken (method)\n\n"
                + "public boolean validateToken() { return true; }\n");
    }

    @Test
    void topLevelSymbolsHaveNoParentPrefix() {
        String text = EmbeddingText.forChunk("app.py", "main", "def", null, "def main(): pass\n");

        assertThat(text).contains("Symbol: main (def)\n");
    }

    @Test
    void queriesUseTheQueryPrefixOnly() {
        assertThat(EmbeddingText.forQuery("where is JWT validated?"))
                .isEqualTo("search_query: where is JWT validated?");
    }

    @Test
    void oversizedCodeIsTruncatedOnlyForEmbedding() {
        String longLine = "x".repeat(EmbeddingText.MAX_EMBEDDED_CODE_CHARS + 500);

        String text = EmbeddingText.forChunk("min.js", "bundle", "function", null, longLine);

        assertThat(text).endsWith("x".repeat(EmbeddingText.MAX_EMBEDDED_CODE_CHARS));
        assertThat(text.length()).isLessThan(EmbeddingText.MAX_EMBEDDED_CODE_CHARS + 200);
    }
}
