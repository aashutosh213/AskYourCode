package com.askyourcode.app.ingestion.model;

import jakarta.persistence.*;

@Entity
@Table(name = "code_chunks")
public class CodeChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(optional = false)
    private FileEntity file;

    private String symbolName;

    private String symbolType;

    // Dotted path of the enclosing type(s), e.g. "OuterService.InnerHelper".
    @Column(name = "parent_symbol")
    private String parentSymbol;

    private int startLine;

    private int endLine;

    // A declaration can legitimately be larger than 10,000 characters.
    // Keep the database mapping as TEXT so indexing does not fail on large methods/classes.
    @Column(columnDefinition = "text")
    private String content;

    public CodeChunkEntity() {
    }

    public CodeChunkEntity(FileEntity file, String symbolName, String symbolType, String parentSymbol,
                           int startLine, int endLine, String content) {
        this.file = file;
        this.symbolName = symbolName;
        this.symbolType = symbolType;
        this.parentSymbol = parentSymbol;
        this.startLine = startLine;
        this.endLine = endLine;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public FileEntity getFile() {
        return file;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public String getSymbolType() {
        return symbolType;
    }

    public String getParentSymbol() {
        return parentSymbol;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getContent() {
        return content;
    }
}
