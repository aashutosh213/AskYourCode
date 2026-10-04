package com.askyourcode.app.ingestion.search;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Shared identifier-aware normalization for code search and reranking. */
final class CodeSearchText {
    private static final Pattern LOWER_TO_UPPER = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern ACRONYM_TO_WORD = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_$]+");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_$]+");

    private CodeSearchText() {}

    static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String separated = ACRONYM_TO_WORD.matcher(text).replaceAll("$1 $2");
        separated = LOWER_TO_UPPER.matcher(separated).replaceAll("$1 $2");
        return NON_IDENTIFIER.matcher(separated).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    static java.util.Set<String> tokens(String text) {
        return TOKEN.matcher(normalize(text)).results()
                .map(match -> match.group())
                .collect(Collectors.toSet());
    }
}
