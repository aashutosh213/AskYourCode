package com.askyourcode.app.ingestion.search;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Shared identifier-aware normalization for code search and reranking. */
final class CodeSearchText {
    private static final Pattern LOWER_TO_UPPER = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern ACRONYM_TO_WORD = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_$]+");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_$]+");

    /**
     * Function words and question words common in natural-language code questions
     * ("how does the ... work", "where is ... implemented"). They occur in almost
     * every chunk, so they add noise to overlap scores without identifying code.
     */
    private static final Set<String> QUESTION_STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "in", "on", "at", "to", "for", "from", "by", "with",
            "is", "are", "was", "were", "be", "it", "its", "this", "that", "there", "here",
            "how", "does", "do", "did", "what", "which", "where", "when", "why", "who",
            "implemented", "used");

    private CodeSearchText() {}

    static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String separated = ACRONYM_TO_WORD.matcher(text).replaceAll("$1 $2");
        separated = LOWER_TO_UPPER.matcher(separated).replaceAll("$1 $2");
        return NON_IDENTIFIER.matcher(separated).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    static Set<String> tokens(String text) {
        return TOKEN.matcher(normalize(text)).results()
                .map(match -> match.group())
                .collect(Collectors.toSet());
    }

    /**
     * Query terms with question/function words removed, in query order. If the
     * query contains nothing else, the original terms are kept so that a query
     * such as "is" still produces a usable (if weak) match.
     */
    static Set<String> queryTerms(String text) {
        Set<String> all = TOKEN.matcher(normalize(text)).results()
                .map(match -> match.group())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> contentTerms = all.stream()
                .filter(term -> !QUESTION_STOPWORDS.contains(term))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return contentTerms.isEmpty() ? all : contentTerms;
    }

    /** The content terms of a query as a space-separated string for BM25 query parsing. */
    static String keywordQuery(String text) {
        return String.join(" ", queryTerms(text));
    }
}
