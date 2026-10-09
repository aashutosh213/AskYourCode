package com.askyourcode.app.ingestion.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSearchTextTest {

    @Test
    void questionWordsAreRemovedFromQueryTerms() {
        assertThat(CodeSearchText.queryTerms("How does the repository scanning work?"))
                .containsExactly("repository", "scanning", "work");
    }

    @Test
    void identifiersAreSplitIntoLowercaseTerms() {
        assertThat(CodeSearchText.keywordQuery("Where is validateToken implemented?"))
                .isEqualTo("validate token");
    }

    @Test
    void queryMadeOnlyOfStopwordsKeepsItsTerms() {
        assertThat(CodeSearchText.queryTerms("is the")).containsExactly("is", "the");
    }
}
