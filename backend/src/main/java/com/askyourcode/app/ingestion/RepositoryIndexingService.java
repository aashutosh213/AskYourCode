package com.askyourcode.app.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class RepositoryIndexingService {

    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    private final Map<String, RepositoryIndexJob> jobs = new ConcurrentHashMap<>();

    private final com.askyourcode.app.ingestion.repo.RepositoryEntityRepository repositoryRepo;
    private final com.askyourcode.app.ingestion.repo.FileEntityRepository fileRepo;
    private final com.askyourcode.app.ingestion.repo.IndexJobRepository jobRepo;
    private final com.askyourcode.app.ingestion.CodeParserService codeParserService;
    private final com.askyourcode.app.ingestion.embedding.EmbeddingService embeddingService;
    @Autowired(required = false)
    private com.askyourcode.app.ingestion.embedding.QdrantEmbeddingClient qdrantClient;

    public RepositoryIndexingService(com.askyourcode.app.ingestion.repo.RepositoryEntityRepository repositoryRepo,
                                     com.askyourcode.app.ingestion.repo.FileEntityRepository fileRepo,
                                     com.askyourcode.app.ingestion.repo.IndexJobRepository jobRepo,
                                     com.askyourcode.app.ingestion.CodeParserService codeParserService,
                                     com.askyourcode.app.ingestion.embedding.EmbeddingService embeddingService) {
        this.repositoryRepo = repositoryRepo;
        this.fileRepo = fileRepo;
        this.jobRepo = jobRepo;
        this.codeParserService = codeParserService;
        this.embeddingService = embeddingService;
    }

    @Transactional(noRollbackFor = Exception.class)
    public RepositoryIndexResponse queueRepositoryIndex(String repositoryPath) {
        Path root = Path.of(repositoryPath);

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path must point to an existing directory.");
        }

        try {
            List<RepositoryFileMetadata> files = repositoryScanner.findCandidateFiles(root)
                    .stream()
                    .map(path -> buildFileMetadata(root, path))
                    .sorted((left, right) -> left.relativePath().compareTo(right.relativePath()))
                    .toList();

            String jobId = UUID.randomUUID().toString();
            String message = "Repository queued for indexing. Candidate files found: " + files.size() + ".";

            // persist repository
            var repoEntity = repositoryRepo.findByPath(root.toString())
                    .orElseGet(() -> repositoryRepo.save(new com.askyourcode.app.ingestion.model.RepositoryEntity(root.toString(), root.getFileName().toString())));

            // persist files
            for (RepositoryFileMetadata meta : files) {
                var fileEntity = new com.askyourcode.app.ingestion.model.FileEntity(meta.relativePath(), meta.fileName(), meta.language(), meta.sizeBytes(), repoEntity);
                fileRepo.save(fileEntity);
            }

            // persist job
            var jobEntity = new com.askyourcode.app.ingestion.model.IndexJobEntity(jobId, root.toString(), "QUEUED", files.size(), Instant.now(), message);
            jobRepo.save(jobEntity);

            // run parsing to create semantic chunks (synchronous for now)
            try {
                codeParserService.parseRepository(root, repoEntity);
            } catch (Exception ex) {
                // parser errors should not prevent indexing response
            }

            // generate embeddings for parsed chunks (local embedding for now)
            try {
                embeddingService.embedRepository(repoEntity);
            } catch (Exception ex) {
                // embedding errors should not prevent indexing response
            }

            // if Qdrant client is available (enabled), push embeddings to the collection for this repo
            if (qdrantClient != null) {
                try {
                    String collection = "repo-" + repoEntity.getId();
                    qdrantClient.pushAllEmbeddings(collection);
                } catch (Exception ex) {
                    // best-effort; do not fail indexing
                }
            }

            // keep in-memory job for immediate access
            jobs.put(jobId, new RepositoryIndexJob(jobId, root.toString(), "QUEUED", files.size(), files, Instant.now(), null, message));

            return new RepositoryIndexResponse(root.toString(), "QUEUED", message, jobId, files);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to scan repository for indexing.", ex);
        }
    }

    public RepositoryIndexJob getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job != null) return job;

        Optional<com.askyourcode.app.ingestion.model.IndexJobEntity> entity = jobRepo.findById(jobId);
        if (entity.isPresent()) {
            var e = entity.get();
            // load files
            var repoOpt = repositoryRepo.findByPath(e.getRepositoryPath());
            List<RepositoryFileMetadata> metas = List.of();
            if (repoOpt.isPresent()) {
                var files = fileRepo.findByRepository(repoOpt.get());
                metas = files.stream()
                        .map(f -> new RepositoryFileMetadata(f.getRelativePath(), f.getFileName(), f.getLanguage(), f.getSizeBytes()))
                        .sorted((l, r) -> l.relativePath().compareTo(r.relativePath()))
                        .toList();
            }
            return new RepositoryIndexJob(e.getId(), e.getRepositoryPath(), e.getStatus(), e.getFilesDiscovered(), metas, e.getStartedAt(), e.getCompletedAt(), e.getMessage());
        }

        return null;
    }

    private RepositoryFileMetadata buildFileMetadata(Path root, Path filePath) {
        long sizeBytes;
        try {
            sizeBytes = Files.size(filePath);
        } catch (IOException ex) {
            sizeBytes = 0L;
        }

        return new RepositoryFileMetadata(
                root.relativize(filePath).toString().replace('\\', '/'),
                filePath.getFileName().toString(),
                detectLanguage(filePath),
                sizeBytes
        );
    }

    private String detectLanguage(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".java")) {
            return "java";
        }
        if (filename.endsWith(".ts") || filename.endsWith(".tsx")) {
            return "typescript";
        }
        if (filename.endsWith(".js") || filename.endsWith(".jsx")) {
            return "javascript";
        }
        if (filename.endsWith(".py")) {
            return "python";
        }
        return "unknown";
    }
}
