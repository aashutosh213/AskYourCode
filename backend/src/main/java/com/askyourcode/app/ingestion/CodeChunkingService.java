package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Persists parser declarations as provenance-preserving retrieval chunks. */
@Service
public class CodeChunkingService {

    private final CodeChunkRepository chunkRepository;

    public CodeChunkingService(CodeChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public int persistChunks(List<ParsedCodeSymbol> symbols) {
        for (ParsedCodeSymbol symbol : symbols) {
            chunkRepository.save(new CodeChunkEntity(symbol.file(), symbol.symbolName(), symbol.symbolType(),
                    symbol.startLine(), symbol.endLine(), symbol.content()));
        }
        return symbols.size();
    }
}
