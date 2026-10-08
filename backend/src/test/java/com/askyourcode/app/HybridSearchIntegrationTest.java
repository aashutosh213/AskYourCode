package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "qdrant.enabled=false")
class HybridSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryIndexingService indexingService;

    @Test
    void returnsKeywordCandidateWhenVectorSearchIsUnavailable(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("hybrid-repo");
        Path sourceDir = repoDir.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("AuthService.java"), """
                package com.example;
                public class AuthService {
                    public boolean validateToken(String token) {
                        return JwtAuthenticationFilter.isValid(token);
                    }
                }
                """);

        IndexingTestSupport.indexAndAwait(mockMvc, objectMapper, indexingService, repoDir.toString());

        mockMvc.perform(post("/api/search/hybrid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VectorSearchRequest("JwtAuthenticationFilter", repoDir.toString(), 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsCount").value(1))
                .andExpect(jsonPath("$.results[0].keywordMatch").value(true))
                .andExpect(jsonPath("$.results[0].vectorMatch").value(false))
                .andExpect(jsonPath("$.results[0].filePath").value("src/main/java/com/example/AuthService.java"));

        // Reranked search is queued like indexing: submit it, wait for the job, then read its result.
        var queued = mockMvc.perform(post("/api/search/reranked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VectorSearchRequest("JwtAuthenticationFilter", repoDir.toString(), 5))))
                .andExpect(status().isAccepted())
                .andReturn();
        String searchJobId = objectMapper.readTree(queued.getResponse().getContentAsString()).get("jobId").asText();
        IndexingTestSupport.awaitSearchJob(mockMvc, objectMapper, searchJobId);

        mockMvc.perform(get("/api/search/jobs/{jobId}", searchJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultsCount").value(1))
                .andExpect(jsonPath("$.result.results[0].rerankScore").isNumber())
                .andExpect(jsonPath("$.result.results[0].retrievalScore").isNumber())
                .andExpect(jsonPath("$.result.results[0].keywordMatch").value(true));
    }
}
