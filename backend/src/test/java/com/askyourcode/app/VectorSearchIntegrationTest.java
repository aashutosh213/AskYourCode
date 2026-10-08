package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "qdrant.enabled=false"
})
public class VectorSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Autowired
    private RepositoryIndexingService indexingService;

    @Test
    void vectorSearchWithQdrantDisabled(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("search-test-repo");
        Files.createDirectories(repoDir.resolve("src/main/java/com/example"));

        String javaSrc = """
            package com.example;
            public class SearchDemo {
                public void findUser() {
                    System.out.println("searching for user");
                }
                public void authenticate() {
                    System.out.println("authenticating user");
                }
            }
            """;
        Files.writeString(repoDir.resolve("src/main/java/com/example/SearchDemo.java"), javaSrc);

        // Index the repository
        IndexingTestSupport.indexAndAwait(mockMvc, objectMapper, indexingService, repoDir.toString());

        // Verify embeddings were created
        var embeddings = embeddingRepository.findAll();
        assertThat(embeddings).isNotEmpty();

        // Try vector search (should handle Qdrant disabled gracefully)
        VectorSearchRequest searchRequest = new VectorSearchRequest("find user", repoDir.toString(), 5);
        mockMvc.perform(post("/api/search/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("find user"))
                .andExpect(jsonPath("$.resultsCount").value(0)); // No results since Qdrant is disabled
    }

    @Test
    void vectorSearchWithInvalidRequest() throws Exception {
        // Empty query
        VectorSearchRequest emptyQuery = new VectorSearchRequest("", "/some/path", 5);
        mockMvc.perform(post("/api/search/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyQuery)))
                .andExpect(status().isBadRequest());

        // Missing repository path
        VectorSearchRequest noRepo = new VectorSearchRequest("test query", "", 5);
        mockMvc.perform(post("/api/search/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noRepo)))
                .andExpect(status().isBadRequest());
    }
}
