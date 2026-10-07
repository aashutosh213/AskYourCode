package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Commits content hashes and the repository snapshot version together. */
@Service
public class IndexSnapshotService {

    private final RepositoryEntityRepository repositoryRepository;
    private final FileEntityRepository fileRepository;

    public IndexSnapshotService(RepositoryEntityRepository repositoryRepository,
                                FileEntityRepository fileRepository) {
        this.repositoryRepository = repositoryRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public long commitSuccessfulIndex(String repositoryPath, Map<String, String> hashesByPath,
                                      boolean contentChanged, String indexedCommit) {
        var repository = repositoryRepository.findByPath(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("Repository metadata disappeared during indexing."));
        var files = fileRepository.findByRepository(repository);
        for (var file : files) {
            String hash = hashesByPath.get(file.getRelativePath());
            if (hash == null) {
                throw new IllegalStateException("Missing scanned content hash for " + file.getRelativePath());
            }
            file.setContentHash(hash);
        }
        fileRepository.saveAll(files);

        if (contentChanged) repository.setIndexVersion(repository.getIndexVersion() + 1);
        repository.setIndexedCommit(indexedCommit);
        repositoryRepository.save(repository);
        return repository.getIndexVersion();
    }
}
