package com.askyourcode.app.ingestion.embedding;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true")
public class QdrantConfig {

    @Value("${qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${qdrant.grpc-port:6334}")
    private int grpcPort;

    @Bean
    public QdrantClient qdrantClient() {
        // Parse host from URL (e.g., http://localhost:6333 -> localhost)
        String host = qdrantUrl.replace("http://", "").replace("https://", "").split(":")[0];

        // Create gRPC client for better performance
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, grpcPort, false).build();

        return new QdrantClient(grpcClient);
    }
}