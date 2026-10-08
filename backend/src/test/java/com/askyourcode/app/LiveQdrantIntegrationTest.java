package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qdrant.enabled=true",
        "qdrant.url=http://localhost:6333",
        "qdrant.grpc-port=6334"
})
class LiveQdrantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryIndexingService indexingService;

    @BeforeAll
    static void requireLiveQdrant() {
        IndexingTestSupport.assumeQdrantAvailable();
    }

    @Test
    void indexesAndRetrievesAChunkThroughLiveQdrant(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("live-qdrant-repo");
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

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VectorSearchRequest("JWT token validation", repoDir.toString(), 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsCount").value(1))
                .andExpect(jsonPath("$.results[0].chunkId").isNumber())
                .andExpect(jsonPath("$.results[0].content").value(org.hamcrest.Matchers.containsString("JwtAuthenticationFilter")))
                .andExpect(jsonPath("$.results[0].filePath")
                        .value("src/main/java/com/example/AuthService.java"));
    }
}
