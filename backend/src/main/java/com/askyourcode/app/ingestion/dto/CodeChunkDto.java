package com.askyourcode.app.ingestion.dto;

public record CodeChunkDto(
        Long id,
        String fileRelativePath,
        String symbolName,
        String symbolType,
        int startLine,
        int endLine,
        String content
) {
}
