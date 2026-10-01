package com.askyourcode.app.ingestion.embedding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmbeddingController {

    private final QdrantEmbeddingClient qdrantClient;

    @Autowired
    public EmbeddingController(org.springframework.beans.factory.ObjectProvider<QdrantEmbeddingClient> clientProvider) {
        this.qdrantClient = clientProvider.getIfAvailable();
    }

    @PostMapping("/api/embeddings/push")
    public ResponseEntity<String> pushEmbeddings(@RequestParam(required = false) String collection) {
        if (qdrantClient == null) {
            return ResponseEntity.status(503).body("Qdrant client not enabled; set qdrant.enabled=true to enable push.");
        }
        String coll = (collection == null || collection.isBlank()) ? "default" : collection;
        try {
            qdrantClient.pushAllEmbeddings(coll);
            return ResponseEntity.accepted().body("Push started for collection: " + coll);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Push failed: " + ex.getMessage());
        }
    }
}
