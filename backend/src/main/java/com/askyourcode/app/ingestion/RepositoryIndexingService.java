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
import com.askyourcode.app.ingestion.embedding.QdrantCollectionNames;
import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.EmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RepositoryIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryIndexingService.class);

    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    private final Map<String, RepositoryIndexJob> jobs = new ConcurrentHashMap<>();

    private final com.askyourcode.app.ingestion.repo.RepositoryEntityRepository repositoryRepo;
    private final com.askyourcode.app.ingestion.repo.FileEntityRepository fileRepo;
    private final com.askyourcode.app.ingestion.repo.IndexJobRepository jobRepo;
    private final CodeChunkRepository chunkRepo;
    private final EmbeddingRepository embeddingRepo;
    private final com.askyourcode.app.ingestion.CodeParserService codeParserService;
    private final CodeChunkingService codeChunkingService;
    private final com.askyourcode.app.ingestion.embedding.EmbeddingService embeddingService;
    private final TaskExecutor indexingTaskExecutor;
    @Autowired(required = false)
    private com.askyourcode.app.ingestion.embedding.QdrantEmbeddingClient qdrantClient;

    public RepositoryIndexingService(com.askyourcode.app.ingestion.repo.RepositoryEntityRepository repositoryRepo,
                                     com.askyourcode.app.ingestion.repo.FileEntityRepository fileRepo,
                                     com.askyourcode.app.ingestion.repo.IndexJobRepository jobRepo,
                                     CodeChunkRepository chunkRepo,
                                     EmbeddingRepository embeddingRepo,
                                     com.askyourcode.app.ingestion.CodeParserService codeParserService,
                                     CodeChunkingService codeChunkingService,
                                     com.askyourcode.app.ingestion.embedding.EmbeddingService embeddingService,
                                     @Qualifier("indexingTaskExecutor") TaskExecutor indexingTaskExecutor) {
        this.repositoryRepo = repositoryRepo;
        this.fileRepo = fileRepo;
        this.jobRepo = jobRepo;
        this.chunkRepo = chunkRepo;
        this.embeddingRepo = embeddingRepo;
        this.codeParserService = codeParserService;
        this.codeChunkingService = codeChunkingService;
        this.embeddingService = embeddingService;
        this.indexingTaskExecutor = indexingTaskExecutor;
    }

    @Transactional(noRollbackFor = Exception.class)
    public RepositoryIndexResponse queueRepositoryIndex(String repositoryPath) {
        return queueRepositoryIndex(repositoryPath, false);
    }

    @Transactional(noRollbackFor = Exception.class)
    public RepositoryIndexResponse queueRepositoryIndex(String repositoryPath, boolean force) {
        Path root = Path.of(repositoryPath);

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path must point to an existing directory.");
        }

        String jobId = UUID.randomUUID().toString();
        String message = "Repository queued for indexing.";

        // persist repository
        var repoEntity = repositoryRepo.findByPath(root.toString())
                .orElseGet(() -> repositoryRepo.save(new com.askyourcode.app.ingestion.model.RepositoryEntity(root.toString(), root.getFileName().toString())));

        // Indexing is idempotent for an already completed repository. The
        // persisted database is the source of truth across app restarts.
        var completedJob = jobRepo
                .findTopByRepositoryPathAndStatusOrderByCompletedAtDesc(root.toString(), "COMPLETED");
        if (completedJob.isPresent() && !force) {
            List<RepositoryFileMetadata> indexedFiles = fileRepo.findByRepository(repoEntity).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            file -> file.getRelativePath(),
                            file -> new RepositoryFileMetadata(file.getRelativePath(), file.getFileName(),
                                    file.getLanguage(), file.getSizeBytes()),
                            (first, ignored) -> first))
                    .values().stream()
                    .sorted((left, right) -> left.relativePath().compareTo(right.relativePath()))
                    .toList();
            var existing = completedJob.get();
            return new RepositoryIndexResponse(root.toString(), "COMPLETED",
                    "Repository is already indexed; skipped duplicate indexing.",
                    existing.getId(), indexedFiles);
        }

        // persist job
        var jobEntity = new com.askyourcode.app.ingestion.model.IndexJobEntity(jobId, root.toString(), "QUEUED", 0, Instant.now(), message);
        jobEntity.setStage("QUEUED");
        jobRepo.save(jobEntity);

        // keep in-memory job for immediate access
        jobs.put(jobId, new RepositoryIndexJob(jobId, root.toString(), "QUEUED", "QUEUED", 0, List.of(), Instant.now(), null, message));

        // Do not start before the repository and job are committed.
        Runnable startTask = () -> dispatchIndexJob(jobId, root, repoEntity);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startTask.run();
                }
            });
        } else {
            startTask.run();
        }

        return new RepositoryIndexResponse(root.toString(), "QUEUED", message, jobId, List.of());
    }

    private void processIndex(String jobId, Path root, com.askyourcode.app.ingestion.model.RepositoryEntity repoEntity) {
        try {
            updateJob(jobId, "RUNNING", "SCANNING", "Scanning repository files.", false);
            List<RepositoryFileMetadata> files = repositoryScanner.findCandidateFiles(root)
                    .stream()
                    .map(path -> buildFileMetadata(root, path))
                    .sorted((left, right) -> left.relativePath().compareTo(right.relativePath()))
                    .toList();

            // A retry or forced re-index starts from a clean metadata/vector
            // snapshot. Wait until scanning succeeds before removing the old one.
            if (qdrantClient != null) {
                qdrantClient.deleteCollection(QdrantCollectionNames.forRepositoryPath(repoEntity.getPath()));
            }
            clearRepository(repoEntity);
            for (RepositoryFileMetadata meta : files) {
                var fileEntity = new FileEntity(meta.relativePath(), meta.fileName(), meta.language(), meta.sizeBytes(), repoEntity);
                fileRepo.save(fileEntity);
            }
            updateDiscovery(jobId, files);

            updateJob(jobId, "RUNNING", "PARSING", "Parsing source files.", false);
            List<ParsedCodeSymbol> parsedSymbols = codeParserService.parseRepository(root, repoEntity);
            updateJob(jobId, "RUNNING", "CHUNKING",
                    "Persisting " + parsedSymbols.size() + " semantic code chunks.", false);
            codeChunkingService.persistChunks(parsedSymbols);
            updateJob(jobId, "RUNNING", "EMBEDDING", "Creating embeddings.", false);
            embeddingService.embedRepository(repoEntity);

            updateJob(jobId, "RUNNING", "STORING", "Storing vectors.", false);
            if (qdrantClient != null) {
                String collection = QdrantCollectionNames.forRepositoryPath(repoEntity.getPath());
                qdrantClient.pushAllEmbeddings(collection, repoEntity.getPath());
            }

            updateJob(jobId, "COMPLETED", "COMPLETED", "Repository indexing completed.", true);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.error("Repository indexing failed for job {}", jobId, ex);
            markJobFailed(jobId, ex);
        }
    }

    private void dispatchIndexJob(String jobId, Path root,
                                  com.askyourcode.app.ingestion.model.RepositoryEntity repoEntity) {
        try {
            indexingTaskExecutor.execute(() -> processIndex(jobId, root, repoEntity));
        } catch (RuntimeException ex) {
            logger.error("Unable to schedule repository indexing job {}", jobId, ex);
            markJobFailed(jobId, ex);
        }
    }

    private void markJobFailed(String jobId, Exception failure) {
        String message = "Repository indexing failed: " + safeMessage(failure);
        if (message.length() > 1900) message = message.substring(0, 1897) + "...";
        try {
            updateJob(jobId, "FAILED", "FAILED", message, true);
        } catch (RuntimeException statusFailure) {
            logger.error("Unable to persist FAILED status for indexing job {}", jobId, statusFailure);
            var current = jobs.get(jobId);
            if (current != null) {
                jobs.put(jobId, new RepositoryIndexJob(current.jobId(), current.repositoryPath(), "FAILED", "FAILED",
                        current.filesDiscovered(), current.files(), current.startedAt(), Instant.now(), message));
            }
        }
    }

    private void updateDiscovery(String jobId, List<RepositoryFileMetadata> files) {
        var entity = jobRepo.findById(jobId).orElseThrow();
        entity.setFilesDiscovered(files.size());
        entity.setMessage("Discovered " + files.size() + " candidate files.");
        jobRepo.save(entity);

        var current = jobs.get(jobId);
        if (current != null) {
            jobs.put(jobId, new RepositoryIndexJob(current.jobId(), current.repositoryPath(), current.status(),
                    current.stage(), files.size(), files, current.startedAt(), current.completedAt(), entity.getMessage()));
        }
    }

    private void clearRepository(com.askyourcode.app.ingestion.model.RepositoryEntity repository) {
        List<FileEntity> files = fileRepo.findByRepository(repository);
        List<CodeChunkEntity> chunks = files.stream()
                .flatMap(file -> chunkRepo.findByFile(file).stream())
                .toList();
        if (!chunks.isEmpty()) embeddingRepo.deleteAll(embeddingRepo.findAllById(
                chunks.stream().flatMap(chunk -> embeddingRepo.findByChunk(chunk).stream()).map(e -> e.getId()).toList()));
        if (!chunks.isEmpty()) chunkRepo.deleteAll(chunks);
        if (!files.isEmpty()) fileRepo.deleteAll(files);
    }

    private void updateJob(String jobId, String status, String stage, String message, boolean completed) {
        Instant completedAt = completed ? Instant.now() : null;
        var current = jobs.get(jobId);
        if (current != null) {
            jobs.put(jobId, new RepositoryIndexJob(current.jobId(), current.repositoryPath(), status, stage,
                    current.filesDiscovered(), current.files(), current.startedAt(),
                    completed ? completedAt : current.completedAt(), message));
        }

        var entity = jobRepo.findById(jobId).orElseThrow();
        entity.setStatus(status);
        entity.setStage(stage);
        entity.setMessage(message);
        if (completed) entity.setCompletedAt(completedAt);
        jobRepo.save(entity);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
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
            return new RepositoryIndexJob(e.getId(), e.getRepositoryPath(), e.getStatus(), e.getStage(), e.getFilesDiscovered(), metas, e.getStartedAt(), e.getCompletedAt(), e.getMessage());
        }

        return null;
    }

    private RepositoryFileMetadata buildFileMetadata(Path root, Path filePath) {
        long sizeBytes;
        try {
            sizeBytes = Files.size(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read candidate file metadata: " + filePath, ex);
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
