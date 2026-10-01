package com.askyourcode.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import com.askyourcode.app.ingestion.RepositoryIndexRequest;
import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RepositoryIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryIndexingService repositoryIndexingService;

    @Test
    void indexEndpointAcceptsLocalRepositoryPath(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("demo-repo");
        Files.createDirectories(repoDir.resolve("src/main/java"));
        Files.createDirectories(repoDir.resolve("target"));
        Files.writeString(repoDir.resolve("src/main/java/App.java"), "class App {}\n");
        Files.writeString(repoDir.resolve("target/generated.txt"), "should be ignored");

        RepositoryIndexRequest request = new RepositoryIndexRequest(repoDir.toString());

        mockMvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.repositoryPath").value(repoDir.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void indexEndpointReturnsDiscoveredFileMetadata(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("demo-repo");
        Files.createDirectories(repoDir.resolve("src/main/java"));
        Files.createDirectories(repoDir.resolve("src/main/js"));
        Files.writeString(repoDir.resolve("src/main/java/App.java"), "class App {}\n");
        Files.writeString(repoDir.resolve("src/main/js/index.js"), "console.log('hi');\n");

        RepositoryIndexRequest request = new RepositoryIndexRequest(repoDir.toString());

        var response = mockMvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.files[0].relativePath").value("src/main/java/App.java"))
                .andExpect(jsonPath("$.files[1].language").value("javascript"))
                .andReturn();

        String jobId = objectMapper.readTree(response.getResponse().getContentAsString()).get("jobId").asText();
        var job = repositoryIndexingService.getJob(jobId);

        assertNotNull(job);
        assertNotNull(job.files());
        assertEquals(2, job.files().size());
        assertEquals("src/main/java/App.java", job.files().getFirst().relativePath());
        assertEquals("javascript", job.files().get(1).language());
    }
}
