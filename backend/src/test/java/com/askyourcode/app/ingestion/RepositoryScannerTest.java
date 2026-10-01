package com.askyourcode.app.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryScannerTest {

    @Test
    void ignoresGeneratedDirectoriesAndCollectsCandidateSourceFiles(@TempDir Path tempDir) throws Exception {
        Path repositoryRoot = tempDir.resolve("repo");
        Files.createDirectories(repositoryRoot.resolve("src/main/java"));
        Files.createDirectories(repositoryRoot.resolve("target/generated"));
        Files.createDirectories(repositoryRoot.resolve("node_modules/pkg"));
        Files.createDirectories(repositoryRoot.resolve("packages/app/target/generated"));
        Files.createDirectories(repositoryRoot.resolve("apps/.venv"));

        Files.writeString(repositoryRoot.resolve("src/main/java/App.java"), "class App {}\n");
        Files.writeString(repositoryRoot.resolve("src/main/java/Other.java"), "class Other {}\n");
        Files.writeString(repositoryRoot.resolve("target/generated/Generated.java"), "class Generated {}\n");
        Files.writeString(repositoryRoot.resolve("node_modules/pkg/index.js"), "console.log('skip');\n");
        Files.writeString(repositoryRoot.resolve("packages/app/target/generated/GeneratedNested.java"), "class Nested {}\n");
        Files.writeString(repositoryRoot.resolve("apps/.venv/skip.py"), "print('skip')\n");

        var scanner = new RepositoryScanner();
        var files = scanner.findCandidateFiles(repositoryRoot);

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(path -> path.endsWith("App.java")));
        assertTrue(files.stream().anyMatch(path -> path.endsWith("Other.java")));
    }
}
