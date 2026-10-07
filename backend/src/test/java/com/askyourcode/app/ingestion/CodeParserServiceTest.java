package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CodeParserServiceTest {

    @Test
    void createsSemanticChunksForBraceAndIndentationLanguages(@TempDir Path tempDir) throws Exception {
        RepositoryEntity repository = new RepositoryEntity(tempDir.toString(), "fixture");
        FileEntity typescript = new FileEntity("src/app.ts", "app.ts", "typescript", 0, repository);
        FileEntity python = new FileEntity("src/service.py", "service.py", "python", 0, repository);
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/app.ts"), "export class AuthService {\n"
                + "  validateToken(token: string) { return token.length > 0; }\n"
                + "}\n"
                + "const refresh = (token: string) => { return token; };\n");
        Files.writeString(tempDir.resolve("src/service.py"), "class AuthService:\n"
                + "    def validate_token(self, token):\n"
                + "        return bool(token)\n\n"
                + "def refresh_token(token):\n"
                + "    return token\n");

        FileEntityRepository fileRepository = mock(FileEntityRepository.class);
        CodeChunkRepository chunkRepository = mock(CodeChunkRepository.class);
        when(fileRepository.findByRepository(repository)).thenReturn(List.of(typescript, python));

        List<ParsedCodeSymbol> symbols = new CodeParserService(fileRepository).parseRepository(tempDir, repository);
        new CodeChunkingService(chunkRepository).persistChunks(symbols);

        ArgumentCaptor<CodeChunkEntity> captor = ArgumentCaptor.forClass(CodeChunkEntity.class);
        verify(chunkRepository, times(6)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(CodeChunkEntity::getSymbolName)
                .contains("AuthService", "validateToken", "refresh", "validate_token", "refresh_token");
        assertThat(captor.getAllValues()).filteredOn(chunk -> "validate_token".equals(chunk.getSymbolName()))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.getStartLine()).isEqualTo(2));
    }
}
