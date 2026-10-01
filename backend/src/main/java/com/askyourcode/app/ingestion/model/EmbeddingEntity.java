package com.askyourcode.app.ingestion.model;

import jakarta.persistence.*;

@Entity
@Table(name = "embeddings")
public class EmbeddingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(optional = false)
    private CodeChunkEntity chunk;

    // A 768-dimensional embedding serialized as JSON is larger than 10 KB.
    // Use a large object so the storage size does not depend on the model dimension.
    @Lob
    private String vectorJson;

    public EmbeddingEntity() {
    }

    public EmbeddingEntity(CodeChunkEntity chunk, String vectorJson) {
        this.chunk = chunk;
        this.vectorJson = vectorJson;
    }

    public Long getId() {
        return id;
    }

    public CodeChunkEntity getChunk() {
        return chunk;
    }

    public String getVectorJson() {
        return vectorJson;
    }
}
