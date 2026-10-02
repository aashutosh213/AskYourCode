package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Calls only the local Ollama chat endpoint; no API key or cloud provider is used. */
@Service
public class LocalLlmService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.chat.model:qwen2.5-coder:7b}")
    private String chatModel;

    public AskResponse answer(String query, HybridSearchResult retrieval) {
        List<AskResponse.Citation> citations = retrieval.getResults().stream()
                .map(hit -> new AskResponse.Citation(
                        retrieval.getResults().indexOf(hit) + 1,
                        hit.filePath(), hit.symbolName(), hit.startLine(), hit.endLine()))
                .toList();
        String context = buildContext(retrieval);
        String prompt = "Answer the user's code question using only the supplied context. "
                + "If the context is insufficient, say so. Cite claims with [n].\n\n"
                + "Question: " + query + "\n\nContext:\n" + context;

        Map<String, Object> request = new HashMap<>();
        request.put("model", chatModel);
        request.put("stream", false);
        request.put("messages", List.of(
                Map.of("role", "system", "content", "You are a precise codebase assistant."),
                Map.of("role", "user", "content", prompt)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<?, ?> response = restTemplate.postForObject(
                ollamaUrl + "/api/chat", new HttpEntity<>(request, headers), Map.class);
        String answer = extractAnswer(response);
        return new AskResponse(query, answer, citations);
    }

    public String buildContext(HybridSearchResult retrieval) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < retrieval.getResults().size(); index++) {
            var hit = retrieval.getResults().get(index);
            context.append('[').append(index + 1).append("] ")
                    .append(hit.filePath()).append(':').append(hit.startLine())
                    .append('-').append(hit.endLine()).append(" symbol=")
                    .append(hit.symbolName()).append('\n')
                    .append(hit.content()).append("\n\n");
        }
        return context.toString();
    }

    private String extractAnswer(Map<?, ?> response) {
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content) || content.isBlank()) {
            throw new IllegalStateException("Ollama returned no chat content");
        }
        return content;
    }
}
