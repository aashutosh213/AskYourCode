package com.askyourcode.app.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RepositoryScanner {

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git",
            "node_modules",
            "dist",
            "build",
            "target",
            "venv",
            ".venv",
            "__pycache__",
            "coverage",
            ".idea",
            ".vscode"
    );

    public List<Path> findCandidateFiles(Path repositoryRoot) throws IOException {
        List<Path> results = new ArrayList<>();
        try (var paths = Files.walk(repositoryRoot)) {
            for (Path path : paths.toList()) {
                if (shouldSkip(path, repositoryRoot)) {
                    continue;
                }
                if (Files.isRegularFile(path) && isRelevantFile(path)) {
                    results.add(path);
                }
            }
        }
        return results.stream()
                .sorted((left, right) -> repositoryRoot.relativize(left).toString().compareToIgnoreCase(repositoryRoot.relativize(right).toString()))
                .toList();
    }

    private boolean shouldSkip(Path path, Path repositoryRoot) {
        if (path.equals(repositoryRoot)) {
            return false;
        }

        Path relative = repositoryRoot.relativize(path);
        for (Path segment : relative) {
            if (IGNORED_DIRS.contains(segment.toString())) {
                return true;
            }
        }

        String normalized = relative.toString().replace('\\', '/');
        return normalized.contains("/node_modules/")
                || normalized.contains("/target/")
                || normalized.contains("/dist/")
                || normalized.contains("/build/")
                || normalized.contains("/coverage/")
                || normalized.contains("/.venv/")
                || normalized.contains("/__pycache__/");
    }

    private boolean isRelevantFile(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".class") || lowerName.endsWith(".jar") || lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")
                || lowerName.endsWith(".pdf") || lowerName.endsWith(".zip") || lowerName.endsWith(".tar")
                || lowerName.endsWith(".gz") || lowerName.endsWith(".exe") || lowerName.endsWith(".dll")) {
            return false;
        }

        return lowerName.endsWith(".java")
                || lowerName.endsWith(".js")
                || lowerName.endsWith(".ts")
                || lowerName.endsWith(".tsx")
                || lowerName.endsWith(".py");
    }
}
