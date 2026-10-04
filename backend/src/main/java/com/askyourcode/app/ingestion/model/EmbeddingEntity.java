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
    // Store it as PostgreSQL text; the Flyway schema uses text rather than an OID large object.
    @Column(name = "vector_json", columnDefinition = "text")
    private String vectorJson;

    @Column(name = "model_key")
    private String modelKey;

    public EmbeddingEntity() {
    }

    public EmbeddingEntity(CodeChunkEntity chunk, String vectorJson) {
        this(chunk, vectorJson, null);
    }

    public EmbeddingEntity(CodeChunkEntity chunk, String vectorJson, String modelKey) {
        this.chunk = chunk;
        this.vectorJson = vectorJson;
        this.modelKey = modelKey;
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

    public String getModelKey() { return modelKey; }

    public void replaceVector(String vectorJson, String modelKey) {
        this.vectorJson = vectorJson;
        this.modelKey = modelKey;
    }
}
