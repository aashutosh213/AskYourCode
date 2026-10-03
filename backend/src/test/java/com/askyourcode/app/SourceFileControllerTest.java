package com.askyourcode.app;

import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SourceFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    @Test
    void returnsSourceWithHighlightedCitationRange(@TempDir Path tempDir) throws Exception {
        Path repository = tempDir.resolve("source-repo");
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("src/App.java"), "package demo;\nclass App {\n  void run() {}\n}\n");
        repositoryRepository.save(new RepositoryEntity(repository.toString(), "source-repo"));

        mockMvc.perform(get("/api/source")
                        .param("repositoryPath", repository.toString())
                        .param("fileRelativePath", "src/App.java")
                        .param("startLine", "2")
                        .param("endLine", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filePath").value("src/App.java"))
                .andExpect(jsonPath("$.lines[1].number").value(2))
                .andExpect(jsonPath("$.lines[1].highlighted").value(true))
                .andExpect(jsonPath("$.lines[0].highlighted").value(false));
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tempDir) throws Exception {
        Path repository = tempDir.resolve("source-repo");
        Files.createDirectories(repository);
        repositoryRepository.save(new RepositoryEntity(repository.toString(), "source-repo"));

        mockMvc.perform(get("/api/source")
                        .param("repositoryPath", repository.toString())
                        .param("fileRelativePath", "../outside.java"))
                .andExpect(status().isNotFound());
    }
}
