package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/source")
public class SourceFileController {

    private static final int CONTEXT_LINES = 20;
    private final RepositoryEntityRepository repositoryRepository;

    public SourceFileController(RepositoryEntityRepository repositoryRepository) {
        this.repositoryRepository = repositoryRepository;
    }

    @GetMapping
    public ResponseEntity<?> getSource(
            @RequestParam String repositoryPath,
            @RequestParam String fileRelativePath,
            @RequestParam(defaultValue = "1") int startLine,
            @RequestParam(defaultValue = "1") int endLine) {
        Path root = Path.of(repositoryPath).toAbsolutePath().normalize();
        if (repositoryRepository.findByPath(repositoryPath)
                .or(() -> repositoryRepository.findByPath(root.toString())).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Repository is not indexed."));
        }

        Path sourceFile = root.resolve(fileRelativePath).normalize();
        if (!sourceFile.startsWith(root) || !Files.isRegularFile(sourceFile) || !isSupportedSource(sourceFile)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Source file was not found in the indexed repository."));
        }

        try {
            Path realRoot = root.toRealPath();
            if (!sourceFile.toRealPath().startsWith(realRoot)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Source file is outside the indexed repository."));
            }
            List<String> lines = Files.readAllLines(sourceFile);
            int safeStart = Math.max(1, startLine);
            int safeEnd = Math.max(safeStart, endLine);
            int contextStart = Math.max(1, safeStart - CONTEXT_LINES);
            int contextEnd = Math.min(lines.size(), safeEnd + CONTEXT_LINES);
            List<SourceLine> sourceLines = java.util.stream.IntStream.rangeClosed(contextStart, contextEnd)
                    .mapToObj(number -> new SourceLine(number, lines.get(number - 1), number >= safeStart && number <= safeEnd))
                    .toList();
            return ResponseEntity.ok(new SourceFileResponse(
                    root.toString(), root.relativize(sourceFile).toString().replace('\\', '/'),
                    safeStart, safeEnd, sourceLines));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Unable to read the source file."));
        }
    }

    private boolean isSupportedSource(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".ts")
                || name.endsWith(".tsx") || name.endsWith(".py");
    }

    public record SourceFileResponse(String repositoryPath, String filePath, int startLine,
                                     int endLine, List<SourceLine> lines) {}

    public record SourceLine(int number, String content, boolean highlighted) {}

    private record ErrorResponse(String error) {}
}
