package com.askyourcode.app;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.askyourcode.app.ingestion.RepositoryIndexJob;
import com.askyourcode.app.ingestion.RepositoryIndexRequest;
import com.askyourcode.app.ingestion.RepositoryIndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/**
 * Indexing runs on a background executor, so a POST to /api/repositories/index only
 * queues the job. Tests use these helpers to wait until the job has finished.
 */
final class IndexingTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MILLIS = 50;

    private IndexingTestSupport() {
    }

    static RepositoryIndexJob indexAndAwait(MockMvc mockMvc, ObjectMapper objectMapper,
                                            RepositoryIndexingService indexingService,
                                            String repositoryPath) throws Exception {
        var response = mockMvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryIndexRequest(repositoryPath))))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = objectMapper.readTree(response.getResponse().getContentAsString()).get("jobId").asText();
        return awaitJob(indexingService, jobId);
    }

    static RepositoryIndexJob awaitJob(RepositoryIndexingService indexingService, String jobId)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (true) {
            RepositoryIndexJob job = indexingService.getJob(jobId);
            if (job != null && ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status()))) {
                assertThat(job.status())
                        .as("indexing job %s failed: %s", jobId, job.message())
                        .isEqualTo("COMPLETED");
                return job;
            }
            if (Instant.now().isAfter(deadline)) {
                fail("Indexing job " + jobId + " did not finish within " + TIMEOUT
                        + "; last status: " + (job == null ? "missing" : job.status()));
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    /** Polls /api/search/jobs/{jobId} until a queued reranked search has completed. */
    static void awaitSearchJob(MockMvc mockMvc, ObjectMapper objectMapper, String jobId) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (true) {
            String body = mockMvc.perform(get("/api/search/jobs/{jobId}", jobId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = objectMapper.readTree(body).path("status").asText();
            if ("COMPLETED".equals(state)) {
                return;
            }
            if ("FAILED".equals(state)) {
                fail("Search job " + jobId + " failed: " + objectMapper.readTree(body).path("message").asText());
            }
            if (Instant.now().isAfter(deadline)) {
                fail("Search job " + jobId + " did not finish within " + TIMEOUT + "; last status: " + state);
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    /** Skips the test (rather than failing it) when the local Qdrant instance is not running. */
    static void assumeQdrantAvailable() {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6333), 500);
            reachable = true;
        } catch (IOException e) {
            reachable = false;
        }
        assumeTrue(reachable, "Qdrant is not running on localhost:6333");
    }
}
