package com.askyourcode.app;

import com.askyourcode.app.ingestion.RepositoryIndexRequest;
import com.askyourcode.app.ingestion.embedding.VectorSearchRequest;
import com.askyourcode.app.ingestion.embedding.VectorSearchService;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import com.askyourcode.app.ingestion.search.HybridSearchService;
import com.askyourcode.app.ingestion.search.KeywordSearchService;
import com.askyourcode.app.ingestion.search.RerankingService;
import com.askyourcode.app.ingestion.search.RetrievalBenchmark;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qdrant.enabled=true",
        "qdrant.url=http://localhost:6333",
        "qdrant.grpc-port=6334"
})
class LiveRetrievalBenchmarkIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RepositoryEntityRepository repositoryRepository;
    @Autowired private FileEntityRepository fileRepository;
    @Autowired private CodeChunkRepository chunkRepository;
    @Autowired private KeywordSearchService keywordSearchService;
    @Autowired private VectorSearchService vectorSearchService;
    @Autowired private HybridSearchService hybridSearchService;
    @Autowired private RerankingService rerankingService;

    @Test
    void comparesAllRetrievalStrategiesAgainstHandLabelledChunks(@TempDir Path tempDir) throws Exception {
        Path repoDir = tempDir.resolve("benchmark-repo");
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
        Files.writeString(sourceDir.resolve("UserService.java"), """
                package com.example;
                public class UserService {
                    public User findUser(String id) {
                        return userRepository.findById(id);
                    }
                }
                """);

        mockMvc.perform(post("/api/repositories/index")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RepositoryIndexRequest(repoDir.toString()))))
                .andExpect(status().isAccepted());

        var repository = repositoryRepository.findByPath(repoDir.toString()).orElseThrow();
        Map<String, Long> expectedIds = fileRepository.findByRepository(repository).stream()
                .flatMap(file -> chunkRepository.findByFile(file).stream())
                .collect(java.util.stream.Collectors.toMap(
                        chunk -> chunk.getSymbolName(), chunk -> chunk.getId()));

        List<RetrievalBenchmark.BenchmarkCase> cases = List.of(
                new RetrievalBenchmark.BenchmarkCase(
                        "Where is JWT token validation implemented?",
                        Set.of(expectedIds.get("validateToken"))),
                new RetrievalBenchmark.BenchmarkCase(
                        "Where is user lookup implemented?",
                        Set.of(expectedIds.get("findUser"))));

        Map<String, Function<String, List<Long>>> strategies = new LinkedHashMap<>();
        strategies.put("keyword", query -> keywordSearchService
                .search(query, repoDir.toString(), 5).getResults().stream()
                .map(hit -> hit.chunkId()).toList());
        strategies.put("vector", query -> vectorSearchService
                .search(query, repoDir.toString(), 5).getResults().stream()
                .map(hit -> hit.getChunkId()).toList());
        strategies.put("hybrid", query -> hybridSearchService
                .search(query, repoDir.toString(), 5).getResults().stream()
                .map(hit -> hit.chunkId()).toList());
        strategies.put("reranked", query -> rerankingService.rerank(
                        query, hybridSearchService.search(query, repoDir.toString(), 5), 5)
                .getResults().stream().map(hit -> hit.chunkId()).toList());

        var report = RetrievalBenchmark.evaluate(cases, 3, strategies);
        System.out.println("Live retrieval benchmark: " + report);

        assertThat(report.strategies()).containsKeys("keyword", "vector", "hybrid", "reranked");
        assertThat(report.strategies().values())
                .allSatisfy(metrics -> assertThat(metrics.meanRecallAtK()).isBetween(0.0, 1.0));
    }
}
