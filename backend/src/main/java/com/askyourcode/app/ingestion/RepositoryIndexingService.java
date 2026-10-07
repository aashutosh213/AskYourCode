package com.askyourcode.app.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final IndexSnapshotService indexSnapshotService;
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
                                     IndexSnapshotService indexSnapshotService,
                                     com.askyourcode.app.ingestion.embedding.EmbeddingService embeddingService,
                                     @Qualifier("indexingTaskExecutor") TaskExecutor indexingTaskExecutor) {
        this.repositoryRepo = repositoryRepo;
        this.fileRepo = fileRepo;
        this.jobRepo = jobRepo;
        this.chunkRepo = chunkRepo;
        this.embeddingRepo = embeddingRepo;
        this.codeParserService = codeParserService;
        this.codeChunkingService = codeChunkingService;
        this.indexSnapshotService = indexSnapshotService;
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

        // persist job
        var jobEntity = new com.askyourcode.app.ingestion.model.IndexJobEntity(jobId, root.toString(), "QUEUED", 0, Instant.now(), message);
        jobEntity.setStage("QUEUED");
        jobRepo.save(jobEntity);

        // keep in-memory job for immediate access
        jobs.put(jobId, new RepositoryIndexJob(jobId, root.toString(), "QUEUED", "QUEUED", 0, List.of(), Instant.now(), null, message));

        // Do not start before the repository and job are committed.
        Runnable startTask = () -> dispatchIndexJob(jobId, root, repoEntity, force);
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

    private void processIndex(String jobId, Path root, com.askyourcode.app.ingestion.model.RepositoryEntity repoEntity,
                              boolean force) {
        try {
            updateJob(jobId, "RUNNING", "SCANNING", "Scanning repository files.", false);
            List<ScannedFile> scannedFiles = repositoryScanner.findCandidateFiles(root)
                    .stream()
                    .map(path -> buildFileMetadata(root, path))
                    .sorted((left, right) -> left.metadata().relativePath().compareTo(right.metadata().relativePath()))
                    .toList();

            FileReconciliation reconciliation = reconcileFiles(repoEntity, scannedFiles, force);

            // Delete only points belonging to changed/deleted files. Unchanged
            // files retain their vectors and collection identity.
            if (reconciliation.contentChanged() && qdrantClient != null) {
                List<Long> staleChunkIds = reconciliation.filesToRemove().stream()
                        .flatMap(file -> chunkRepo.findByFile(file).stream())
                        .map(CodeChunkEntity::getId).toList();
                qdrantClient.deleteChunkPoints(
                        QdrantCollectionNames.forRepositoryPath(repoEntity.getPath()), staleChunkIds);
            }
            List<FileEntity> filesToParse = applyFileReconciliation(repoEntity, reconciliation);
            updateDiscovery(jobId, scannedFiles.stream().map(ScannedFile::metadata).toList(), reconciliation);

            updateJob(jobId, "RUNNING", "PARSING", "Parsing source files.", false);
            List<ParsedCodeSymbol> parsedSymbols = codeParserService.parseFiles(root, filesToParse);
            updateJob(jobId, "RUNNING", "CHUNKING",
                    "Persisting " + parsedSymbols.size() + " semantic code chunks.", false);
            codeChunkingService.persistChunks(parsedSymbols);
            updateJob(jobId, "RUNNING", "EMBEDDING", "Creating embeddings.", false);
            embeddingService.embedRepository(repoEntity);

            updateJob(jobId, "RUNNING", "STORING", "Storing vectors.", false);
            if (qdrantClient != null) {
                String collection = QdrantCollectionNames.forRepositoryPath(repoEntity.getPath());
                if (!filesToParse.isEmpty()) {
                    qdrantClient.pushEmbeddingsForFiles(collection, filesToParse);
                } else if (!qdrantClient.collectionExists(collection)) {
                    // Recover a lost/removed vector collection without forcing
                    // callers to modify source files just to rebuild it.
                    qdrantClient.pushAllEmbeddings(collection, repoEntity.getPath());
                }
            }

            Map<String, String> hashesByPath = scannedFiles.stream().collect(java.util.stream.Collectors.toMap(
                    scanned -> scanned.metadata().relativePath(), ScannedFile::contentHash));
            long indexVersion = indexSnapshotService.commitSuccessfulIndex(
                    repoEntity.getPath(), hashesByPath, reconciliation.contentChanged(),
                    GitRevisionReader.readHead(root));
            updateJob(jobId, "COMPLETED", "COMPLETED",
                    "Repository indexing completed at index version " + indexVersion + ".", true);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.error("Repository indexing failed for job {}", jobId, ex);
            markJobFailed(jobId, ex);
        }
    }

    private void dispatchIndexJob(String jobId, Path root,
                                  com.askyourcode.app.ingestion.model.RepositoryEntity repoEntity, boolean force) {
        try {
            indexingTaskExecutor.execute(() -> processIndex(jobId, root, repoEntity, force));
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

    private FileReconciliation reconcileFiles(com.askyourcode.app.ingestion.model.RepositoryEntity repository,
                                              List<ScannedFile> scannedFiles, boolean force) {
        Map<String, List<FileEntity>> existingByPath = fileRepo.findByRepository(repository).stream()
                .collect(java.util.stream.Collectors.groupingBy(FileEntity::getRelativePath));
        List<FileEntity> unchangedFiles = new java.util.ArrayList<>();
        List<FileEntity> filesToRemove = new java.util.ArrayList<>();
        List<ScannedFile> filesToCreate = new java.util.ArrayList<>();
        for (ScannedFile scanned : scannedFiles) {
            String relativePath = scanned.metadata().relativePath();
            List<FileEntity> existing = existingByPath.remove(relativePath);
            FileEntity current = existing == null || existing.isEmpty() ? null : existing.get(0);
            if (existing != null && existing.size() > 1) filesToRemove.addAll(existing.subList(1, existing.size()));

            if (!force && current != null && scanned.contentHash().equals(current.getContentHash())) {
                unchangedFiles.add(current);
            } else {
                if (current != null) filesToRemove.add(current);
                filesToCreate.add(scanned);
            }
        }

        existingByPath.values().forEach(filesToRemove::addAll);
        List<FileEntity> allFilesToRemove = filesToRemove.stream().distinct().toList();
        boolean changed = force || !filesToCreate.isEmpty() || !allFilesToRemove.isEmpty();
        return new FileReconciliation(unchangedFiles, filesToCreate, allFilesToRemove, changed);
    }

    private List<FileEntity> applyFileReconciliation(com.askyourcode.app.ingestion.model.RepositoryEntity repository,
                                                     FileReconciliation reconciliation) {
        reconciliation.filesToRemove().forEach(this::deleteFileData);
        List<FileEntity> newFiles = reconciliation.filesToCreate().stream()
                .map(scanned -> scanned.metadata())
                .map(meta -> new FileEntity(meta.relativePath(), meta.fileName(), meta.language(), meta.sizeBytes(), repository))
                .map(fileRepo::save)
                .toList();
        return newFiles;
    }

    private void deleteFileData(FileEntity file) {
        List<CodeChunkEntity> chunks = chunkRepo.findByFile(file);
        if (!chunks.isEmpty()) {
            List<Long> embeddingIds = chunks.stream()
                    .flatMap(chunk -> embeddingRepo.findByChunk(chunk).stream())
                    .map(com.askyourcode.app.ingestion.model.EmbeddingEntity::getId)
                    .toList();
            if (!embeddingIds.isEmpty()) embeddingRepo.deleteAllById(embeddingIds);
            chunkRepo.deleteAll(chunks);
        }
        fileRepo.delete(file);
    }

    private void updateDiscovery(String jobId, List<RepositoryFileMetadata> files,
                                 FileReconciliation reconciliation) {
        String message = "Discovered " + files.size() + " candidate files: "
                + reconciliation.filesToCreate().size() + " changed/new, "
                + reconciliation.unchangedFiles().size() + " unchanged, "
                + reconciliation.filesToRemove().size() + " removed.";
        var entity = jobRepo.findById(jobId).orElseThrow();
        entity.setFilesDiscovered(files.size());
        entity.setMessage(message);
        jobRepo.save(entity);

        var current = jobs.get(jobId);
        if (current != null) {
            jobs.put(jobId, new RepositoryIndexJob(current.jobId(), current.repositoryPath(), current.status(),
                    current.stage(), files.size(), files, current.startedAt(), current.completedAt(), message));
        }
    }

    private record ScannedFile(RepositoryFileMetadata metadata, String contentHash) {}

    private record FileReconciliation(List<FileEntity> unchangedFiles, List<ScannedFile> filesToCreate,
                                      List<FileEntity> filesToRemove,
                                      boolean contentChanged) {}

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

    private ScannedFile buildFileMetadata(Path root, Path filePath) {
        long sizeBytes;
        String contentHash;
        try {
            sizeBytes = Files.size(filePath);
            contentHash = sha256(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read candidate file metadata: " + filePath, ex);
        }

        RepositoryFileMetadata metadata = new RepositoryFileMetadata(
                root.relativize(filePath).toString().replace('\\', '/'),
                filePath.getFileName().toString(), detectLanguage(filePath), sizeBytes);
        return new ScannedFile(metadata, contentHash);
    }

    private String sha256(Path filePath) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable for source file hashing.", ex);
        }
        try (InputStream input = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
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
