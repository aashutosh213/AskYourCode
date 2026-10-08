package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects a bounded, provenance-preserving subset of retrieved code for the LLM prompt. */
@Component
public class AskContextBuilder {
    private static final int DEFAULT_MAX_CHARACTERS = 12_000;

    @Value("${ask.context.max-characters:12000}")
    private int maxCharacters = DEFAULT_MAX_CHARACTERS;

    AskContextBuilder() {}

    AskContextBuilder(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public BuiltContext build(HybridSearchResult retrieval) {
        if (retrieval == null || retrieval.getResults() == null || retrieval.getResults().isEmpty()) {
            return new BuiltContext("", List.of(), List.of());
        }

        int budget = Math.max(0, maxCharacters);
        StringBuilder text = new StringBuilder();
        List<HybridSearchResult.SearchHit> selected = new ArrayList<>();
        List<AskResponse.Citation> citations = new ArrayList<>();
        Set<Object> seen = new HashSet<>();

        for (HybridSearchResult.SearchHit hit : retrieval.getResults()) {
            if (hit == null || hit.content() == null || hit.content().isBlank() || !seen.add(key(hit))) {
                continue;
            }

            int citationNumber = selected.size() + 1;
            String block = format(citationNumber, hit);
            int addedCharacters = block.length() + (text.isEmpty() ? 0 : 2);
            // Keep declarations intact; an oversized hit is skipped so a
            // smaller lower-ranked hit may still fit in the remaining budget.
            if (addedCharacters > budget - text.length()) continue;

            if (!text.isEmpty()) text.append("\n\n");
            text.append(block);
            selected.add(hit);
            citations.add(new AskResponse.Citation(citationNumber, hit.filePath(), hit.symbolName(),
                    hit.startLine(), hit.endLine()));
        }

        return new BuiltContext(text.toString(), List.copyOf(selected), List.copyOf(citations));
    }

    private Object key(HybridSearchResult.SearchHit hit) {
        if (hit.chunkId() != null) return new ChunkIdKey(hit.chunkId());
        return new ChunkContentKey(hit.filePath(), hit.startLine(), hit.endLine(), hit.symbolName(), hit.content());
    }

    private String format(int citationNumber, HybridSearchResult.SearchHit hit) {
        return "[" + citationNumber + "] " + hit.filePath() + ":" + hit.startLine() + "-" + hit.endLine()
                + " symbol=" + hit.symbolName() + "\n" + hit.content();
    }

    public record BuiltContext(String text, List<HybridSearchResult.SearchHit> selectedHits,
                               List<AskResponse.Citation> citations) {}

    private record ChunkIdKey(Long id) {}

    private record ChunkContentKey(String filePath, int startLine, int endLine, String symbolName, String content) {}
}
