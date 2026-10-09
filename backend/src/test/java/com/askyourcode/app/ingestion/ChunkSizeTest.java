package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSizeTest {

    @Test
    void oversizedMethodIsSplitIntoContiguousWindowsWithTheirOwnLineRanges(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity java = new FileEntity("Big.java", "Big.java", "java", 0, repository);
        String body = IntStream.rangeClosed(1, 400)
                .mapToObj(i -> "        total += compute(" + i + ");")
                .reduce((a, b) -> a + "\n" + b).orElse("");
        Files.writeString(tempDir.resolve("Big.java"), "public class Big {\n"
                + "    void run() {\n" + body + "\n    }\n}\n");

        List<ParsedCodeSymbol> parts = new CodeParserService(null).parseFiles(tempDir, List.of(java)).stream()
                .filter(symbol -> symbol.symbolName().equals("run")).toList();

        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts).allSatisfy(part -> {
            assertThat(part.content().length()).isLessThanOrEqualTo(CodeParserService.MAX_CHUNK_CHARS);
            assertThat(part.symbolType()).isEqualTo("method");
            assertThat(part.parentSymbol()).isEqualTo("Big");
        });
        // Windows are consecutive and cover the whole method, so no code is lost.
        assertThat(parts.get(0).startLine()).isEqualTo(2);
        for (int i = 1; i < parts.size(); i++) {
            assertThat(parts.get(i).startLine()).isEqualTo(parts.get(i - 1).endLine() + 1);
        }
        assertThat(parts.get(parts.size() - 1).endLine()).isEqualTo(403);
    }

    @Test
    void expressionBodiedArrowEndsWithItsBodyInsteadOfRunningToEndOfFile(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity tsx = new FileEntity("view.tsx", "view.tsx", "typescript", 0, repository);
        Files.writeString(tempDir.resolve("view.tsx"), String.join("\n",
                "export const Badge = (label: string) =>",
                "  <span>{label}</span>;",
                "",
                "function other() {",
                "  return 1;",
                "}",
                ""));

        List<ParsedCodeSymbol> symbols = new CodeParserService(null).parseFiles(tempDir, List.of(tsx));

        ParsedCodeSymbol badge = symbols.stream().filter(s -> s.symbolName().equals("Badge")).findFirst().orElseThrow();
        assertThat(badge.startLine()).isEqualTo(1);
        assertThat(badge.endLine()).isEqualTo(2);
        assertThat(symbols.stream().filter(s -> s.symbolName().equals("other")).findFirst()).isPresent();
    }
}
