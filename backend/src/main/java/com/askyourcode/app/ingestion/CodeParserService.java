package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class CodeParserService {

    private final FileEntityRepository fileRepo;
    private final CodeChunkRepository chunkRepo;

    public CodeParserService(FileEntityRepository fileRepo, CodeChunkRepository chunkRepo) {
        this.fileRepo = fileRepo;
        this.chunkRepo = chunkRepo;
    }

    public void parseRepository(Path root, RepositoryEntity repository) {
        List<FileEntity> files = fileRepo.findByRepository(repository);
        for (FileEntity f : files) {
            if (!"java".equalsIgnoreCase(f.getLanguage())) continue;
            Path filePath = root.resolve(f.getRelativePath());
            try {
                String content = Files.readString(filePath);
                CompilationUnit cu = StaticJavaParser.parse(content);

                // methods
                cu.findAll(MethodDeclaration.class).forEach(m -> handleCallable(f, content, m));
                cu.findAll(ConstructorDeclaration.class).forEach(c -> handleCallable(f, content, c));

            } catch (IOException ex) {
                // ignore parse errors for now
            }
        }
    }

    private void handleCallable(FileEntity f, String fullContent, CallableDeclaration<?> decl) {
        if (!decl.getRange().isPresent()) return;
        int start = decl.getRange().get().begin.line;
        int end = decl.getRange().get().end.line;
        String[] lines = fullContent.split("\r?\n");
        int s = Math.max(1, start);
        int e = Math.min(lines.length, end);
        StringBuilder sb = new StringBuilder();
        for (int i = s; i <= e; i++) {
            sb.append(lines[i-1]).append("\n");
        }

        String symbol = decl.getNameAsString();
        String type = decl instanceof MethodDeclaration ? "method" : "constructor";

        // find file entity from repo
        FileEntity fileEntity = f;

        CodeChunkEntity chunk = new CodeChunkEntity(fileEntity, symbol, type, start, end, sb.toString());
        chunkRepo.save(chunk);
    }
}
