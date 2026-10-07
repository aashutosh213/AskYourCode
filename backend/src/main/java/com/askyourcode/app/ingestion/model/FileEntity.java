package com.askyourcode.app.ingestion.model;

import jakarta.persistence.*;

@Entity
@Table(name = "files")
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String relativePath;

    private String fileName;

    private String language;

    private long sizeBytes;

    @Column(length = 64)
    private String contentHash;

    @ManyToOne(optional = false)
    private RepositoryEntity repository;

    public FileEntity() {
    }

    public FileEntity(String relativePath, String fileName, String language, long sizeBytes, RepositoryEntity repository) {
        this.relativePath = relativePath;
        this.fileName = fileName;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.repository = repository;
    }

    public Long getId() {
        return id;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getLanguage() {
        return language;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentHash() { return contentHash; }

    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public RepositoryEntity getRepository() {
        return repository;
    }
}
