package com.askyourcode.app.ingestion.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "index_jobs")
public class IndexJobEntity {

    @Id
    private String id;

    private String repositoryPath;

    private String status;

    @Column(length = 40)
    private String stage;

    private int filesDiscovered;

    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 2000)
    private String message;

    public IndexJobEntity() {
    }

    public IndexJobEntity(String id, String repositoryPath, String status, int filesDiscovered, Instant startedAt, String message) {
        this.id = id;
        this.repositoryPath = repositoryPath;
        this.status = status;
        this.filesDiscovered = filesDiscovered;
        this.startedAt = startedAt;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getStatus() {
        return status;
    }

    public String getStage() { return stage; }

    public int getFilesDiscovered() {
        return filesDiscovered;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStage(String stage) { this.stage = stage; }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
