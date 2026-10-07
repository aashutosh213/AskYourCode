package com.askyourcode.app.ingestion.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "repositories")
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String path;

    private String name;

    @Column(nullable = false)
    private long indexVersion;

    @Column(length = 64)
    private String indexedCommit;

    private Instant createdAt = Instant.now();

    public RepositoryEntity() {
    }

    public RepositoryEntity(String path, String name) {
        this.path = path;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long getIndexVersion() { return indexVersion; }

    public void setIndexVersion(long indexVersion) { this.indexVersion = indexVersion; }

    public String getIndexedCommit() { return indexedCommit; }

    public void setIndexedCommit(String indexedCommit) { this.indexedCommit = indexedCommit; }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
