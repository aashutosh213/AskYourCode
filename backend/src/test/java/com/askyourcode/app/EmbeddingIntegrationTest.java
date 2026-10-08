package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
public class EmbeddingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Autowired
    private RepositoryIndexingService indexingService;

    @Test
    void embeddingsCreatedAfterIndexing(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("demo-repo");
        Files.createDirectories(repoDir.resolve("src/main/java/com/example"));
        String javaSrc = "package com.example;\npublic class App {\n    public void hello() { System.out.println(\"hi\"); }\n}\n";
        Files.writeString(repoDir.resolve("src/main/java/com/example/App.java"), javaSrc);

        IndexingTestSupport.indexAndAwait(mockMvc, objectMapper, indexingService, repoDir.toString());

        // embedding repository should have at least one entry
        var list = embeddingRepository.findAll();
        assertThat(list).isNotEmpty();
    }
}
