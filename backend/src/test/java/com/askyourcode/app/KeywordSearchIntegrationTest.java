package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexRequest;
import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KeywordSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchesExactCodeIdentifiersWithinOneRepository(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("keyword-repo");
        Path sourceDir = repoDir.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("AuthService.java"), """
                package com.example;
                public class AuthService {
                    public boolean validateToken(String token) {
                        return JwtAuthenticationFilter.isValid(token);
                    }
                    public void unrelated() { System.out.println("health"); }
                }
                """);

        mockMvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryIndexRequest(repoDir.toString()))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/search/keyword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VectorSearchRequest("JwtAuthenticationFilter", repoDir.toString(), 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsCount").value(1))
                .andExpect(jsonPath("$.results[0].filePath").value("src/main/java/com/example/AuthService.java"))
                .andExpect(jsonPath("$.results[0].symbolName").value("validateToken"))
                .andExpect(jsonPath("$.results[0].startLine").value(3));
    }

    @Test
    void rejectsInvalidKeywordSearchRequest() throws Exception {
        mockMvc.perform(post("/api/search/keyword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VectorSearchRequest("", "/some/path", 5))))
                .andExpect(status().isBadRequest());
    }
}
