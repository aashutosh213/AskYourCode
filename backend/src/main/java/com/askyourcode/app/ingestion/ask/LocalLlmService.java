package com.askyourcode.app.ingestion.ask;

import com.askyourcode.app.ingestion.search.HybridSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

/** Calls only the local Ollama chat endpoint; no API key or cloud provider is used. */
@Service
public class LocalLlmService {
    private static final int OLLAMA_CONNECT_TIMEOUT_MS = 5_000;
    private static final int OLLAMA_READ_TIMEOUT_MS = 180_000;
    private final RestTemplate restTemplate = createRestTemplate();
    private AskContextBuilder contextBuilder = new AskContextBuilder();

    @Autowired
    public void setContextBuilder(AskContextBuilder contextBuilder) {
        this.contextBuilder = contextBuilder;
    }

    private static final int MAX_CHAT_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 1_000L;
    private static final Pattern CITATION_REFERENCE = Pattern.compile("\\[(\\d+)]");
    private static final String INSUFFICIENT_EVIDENCE_ANSWER =
            "I couldn't find enough usable code evidence to answer this question. "
                    + "Try a more specific query or re-index the repository.";

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.chat.model:qwen2.5-coder:0.5b}")
    private String chatModel;

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(OLLAMA_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(OLLAMA_READ_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    public AskResponse answer(String query, HybridSearchResult retrieval) {
        AskContextBuilder.BuiltContext selectedContext = contextBuilder.build(retrieval);
        if (selectedContext.selectedHits().isEmpty()) {
            return new AskResponse(query, INSUFFICIENT_EVIDENCE_ANSWER, List.of(), true);
        }
        String prompt = "Answer the user's code question using only the supplied context. "
                + "If the context is insufficient, say so. Cite claims with [n]. "
                + "Only use citation numbers present in the supplied context.\n\n"
                + "Question: " + query + "\n\nContext:\n" + selectedContext.text();

        Map<String, Object> request = new HashMap<>();
        request.put("model", chatModel);
        request.put("stream", false);
        request.put("messages", List.of(
                Map.of("role", "system", "content", "You are a precise codebase assistant."),
                Map.of("role", "user", "content", prompt)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<?, ?> response = callOllamaWithRetry(request, headers);
        String answer = retainSelectedCitations(extractAnswer(response), selectedContext.citations());
        return new AskResponse(query, answer, selectedContext.citations(), false);
    }

    static String retainSelectedCitations(String answer, List<AskResponse.Citation> citations) {
        Set<String> allowedReferences = citations.stream()
                .map(citation -> "[" + citation.number() + "]")
                .collect(java.util.stream.Collectors.toSet());
        Matcher matcher = CITATION_REFERENCE.matcher(answer);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            String reference = matcher.group();
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(
                    allowedReferences.contains(reference) ? reference : "[unverified citation]"));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    Map<?, ?> callOllamaWithRetry(Map<String, Object> request, HttpHeaders headers) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_CHAT_ATTEMPTS; attempt++) {
            try {
                return restTemplate.postForObject(
                        ollamaUrl + "/api/chat", new HttpEntity<>(request, headers), Map.class);
            } catch (RestClientException ex) {
                lastFailure = ex;
                if (attempt == MAX_CHAT_ATTEMPTS) {
                    break;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Ollama chat request failed at " + ollamaUrl
                + " using model '" + chatModel + "': "
                + (lastFailure == null ? "no response" : lastFailure.getMessage()), lastFailure);
    }

    public String buildContext(HybridSearchResult retrieval) {
        return contextBuilder.build(retrieval).text();
    }

    private String extractAnswer(Map<?, ?> response) {
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content) || content.isBlank()) {
            throw new IllegalStateException("Ollama returned no chat content");
        }
        return content;
    }
}
