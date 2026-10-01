package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.dto.CodeChunkDto;
import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CodeChunkController {

    private final RepositoryEntityRepository repositoryRepo;
    private final FileEntityRepository fileRepo;
    private final CodeChunkRepository chunkRepo;

    public CodeChunkController(RepositoryEntityRepository repositoryRepo, FileEntityRepository fileRepo, CodeChunkRepository chunkRepo) {
        this.repositoryRepo = repositoryRepo;
        this.fileRepo = fileRepo;
        this.chunkRepo = chunkRepo;
    }

    @GetMapping("/api/chunks")
    public List<CodeChunkDto> getChunksByRepositoryPath(
            @RequestParam String repositoryPath,
            @RequestParam(required = false) String fileRelativePath,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var repoOpt = repositoryRepo.findByPath(repositoryPath);
        if (repoOpt.isEmpty()) return List.of();
        RepositoryEntity repo = repoOpt.get();

        if (fileRelativePath != null && !fileRelativePath.isBlank()) {
            var fileOpt = fileRepo.findByRepositoryAndRelativePath(repo, fileRelativePath);
            if (fileOpt.isEmpty()) return List.of();
            var file = fileOpt.get();
            var pageReq = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
            var chunkPage = chunkRepo.findByFile(file, pageReq);
            List<CodeChunkDto> out = new ArrayList<>();
            for (CodeChunkEntity c : chunkPage.getContent()) {
                out.add(new CodeChunkDto(c.getId(), file.getRelativePath(), c.getSymbolName(), c.getSymbolType(), c.getStartLine(), c.getEndLine(), c.getContent()));
            }
            return out;
        }

        // no file filter: collect all chunks and apply simple pagination
        List<FileEntity> files = fileRepo.findByRepository(repo);
        List<CodeChunkDto> all = new ArrayList<>();
        for (FileEntity f : files) {
            List<CodeChunkEntity> chunks = chunkRepo.findByFile(f);
            for (CodeChunkEntity c : chunks) {
                all.add(new CodeChunkDto(c.getId(), f.getRelativePath(), c.getSymbolName(), c.getSymbolType(), c.getStartLine(), c.getEndLine(), c.getContent()));
            }
        }

        int from = Math.max(0, page * size);
        if (from >= all.size()) return List.of();
        int to = Math.min(all.size(), from + size);
        return all.subList(from, to);
    }
}
