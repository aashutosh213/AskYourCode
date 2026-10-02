package com.askyourcode.app.ingestion.ask;

import java.util.List;

public record AskResponse(String query, String answer, List<Citation> citations) {
    public record Citation(int number, String filePath, String symbolName,
                           int startLine, int endLine) {
    }
}
