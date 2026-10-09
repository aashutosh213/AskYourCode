package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeParserParentTest {

    @Test
    void javaTypesGetHeaderChunksAndMembersKeepTheirEnclosingType(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity java = new FileEntity("AuthService.java", "AuthService.java", "java", 0, repository);
        Files.writeString(tempDir.resolve("AuthService.java"), String.join("\n",
                "public class AuthService {",
                "    private final int limit;",
                "    public AuthService(int limit) { this.limit = limit; }",
                "    public boolean validateToken(String token) { return true; }",
                "    public static class Helper {",
                "        void decode() { }",
                "    }",
                "}",
                ""));

        List<ParsedCodeSymbol> symbols = new CodeParserService(null).parseFiles(tempDir, List.of(java));

        ParsedCodeSymbol service = find(symbols, "AuthService", "class");
        assertThat(service.parentSymbol()).isNull();
        assertThat(service.startLine()).isEqualTo(1);
        // The header stops before the first member (the field on line 2).
        assertThat(service.endLine()).isEqualTo(1);
        assertThat(service.content()).contains("public class AuthService").doesNotContain("validateToken");

        assertThat(find(symbols, "validateToken", "method").parentSymbol()).isEqualTo("AuthService");
        assertThat(find(symbols, "AuthService", "constructor").parentSymbol()).isEqualTo("AuthService");

        ParsedCodeSymbol helper = find(symbols, "Helper", "class");
        assertThat(helper.parentSymbol()).isEqualTo("AuthService");
        assertThat(find(symbols, "decode", "method").parentSymbol()).isEqualTo("AuthService.Helper");
    }

    @Test
    void pythonNestedDefinitionsRecordTheirEnclosingClasses(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity python = new FileEntity("service.py", "service.py", "python", 0, repository);
        Files.writeString(tempDir.resolve("service.py"), String.join("\n",
                "class Outer:",
                "    class Inner:",
                "        def run(self):",
                "            return 1",
                "",
                "def top():",
                "    return 2",
                ""));

        List<ParsedCodeSymbol> symbols = new CodeParserService(null).parseFiles(tempDir, List.of(python));

        assertThat(find(symbols, "Inner", "class").parentSymbol()).isEqualTo("Outer");
        assertThat(find(symbols, "run", "def").parentSymbol()).isEqualTo("Outer.Inner");
        assertThat(find(symbols, "top", "def").parentSymbol()).isNull();
    }

    @Test
    void java21SyntaxIsParsedWithoutFailingTheIndexingJob(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity java = new FileEntity("Modern.java", "Modern.java", "java", 0, repository);
        Files.writeString(tempDir.resolve("Modern.java"), String.join("\n",
                "public record Modern(int value) {",
                "    String describe(Object o) {",
                "        return switch (o) {",
                "            case Integer i when i > 0 -> \"positive\";",
                "            default -> \"other\";",
                "        };",
                "    }",
                "}",
                ""));

        List<ParsedCodeSymbol> symbols = new CodeParserService(null).parseFiles(tempDir, List.of(java));

        assertThat(find(symbols, "Modern", "record").parentSymbol()).isNull();
        assertThat(find(symbols, "describe", "method").parentSymbol()).isEqualTo("Modern");
    }

    @Test
    void everyBackendSourceFileParses(@TempDir Path tempDir) throws Exception {
        // Dogfooding: the indexer must accept the code it is written in.
        Path sourceRoot = Path.of("src/main/java");
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "backend");
        List<FileEntity> files;
        try (var paths = Files.walk(sourceRoot)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> new FileEntity(sourceRoot.relativize(path).toString(),
                            path.getFileName().toString(), "java", 0, repository))
                    .toList();
        }

        assertThat(new CodeParserService(null).parseFiles(sourceRoot, files)).isNotEmpty();
    }

    private static ParsedCodeSymbol find(List<ParsedCodeSymbol> symbols, String name, String type) {
        return symbols.stream()
                .filter(symbol -> symbol.symbolName().equals(name) && symbol.symbolType().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + " " + name + " in " + symbols));
    }
}
