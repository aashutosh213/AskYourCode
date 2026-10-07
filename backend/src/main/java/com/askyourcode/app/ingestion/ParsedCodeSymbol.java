package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.FileEntity;

/** A parser result with source provenance, before it is persisted as a search chunk. */
public record ParsedCodeSymbol(
        FileEntity file,
        String symbolName,
        String symbolType,
        int startLine,
        int endLine,
        String content
) {
}
