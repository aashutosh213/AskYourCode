package com.askyourcode.app.ingestion.embedding;

/**
 * Builds the exact text sent to the embedding model for code chunks and queries.
 *
 * <p>nomic-embed-text is trained with task prefixes: passages are embedded with
 * {@code search_document:} and queries with {@code search_query:}. A chunk's
 * file path and symbol header are included in the document text because a
 * natural-language query often names the file or class, which the code body
 * alone does not say.
 *
 * <p>{@link #FORMAT_VERSION} is part of the stored embedding model key. Bump it
 * whenever this format changes so that existing vectors are regenerated on the
 * next index instead of silently mixing two document formats.
 */
public final class EmbeddingText {

    public static final String FORMAT_VERSION = "enriched-v2";

    /**
     * Characters of code embedded per chunk. nomic-embed-text accepts 2048 tokens and
     * rejects longer input. Parsing already splits large symbols, so this only bounds a
     * single very long line such as minified code. Stored content is not truncated.
     */
    public static final int MAX_EMBEDDED_CODE_CHARS = 4000;

    static final String DOCUMENT_PREFIX = "search_document: ";
    static final String QUERY_PREFIX = "search_query: ";

    private EmbeddingText() {}

    public static String forChunk(String filePath, String symbolName, String symbolType,
                                  String parentSymbol, String content) {
        String qualifiedName = parentSymbol == null || parentSymbol.isBlank()
                ? symbolName
                : parentSymbol + "." + symbolName;
        return DOCUMENT_PREFIX
                + "File: " + filePath + "\n"
                + "Symbol: " + qualifiedName + " (" + symbolType + ")\n\n"
                + truncate(content);
    }

    private static String truncate(String content) {
        if (content == null) return "";
        return content.length() <= MAX_EMBEDDED_CODE_CHARS
                ? content
                : content.substring(0, MAX_EMBEDDED_CODE_CHARS);
    }

    public static String forQuery(String query) {
        return QUERY_PREFIX + query;
    }
}
