package com.askyourcode.app.ingestion.embedding;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class QdrantCollectionNames {
    private QdrantCollectionNames() {
    }

    /** Stable across H2 restarts, while remaining safe as a Qdrant name. */
    public static String forRepositoryPath(String repositoryPath) {
        String stableId = UUID.nameUUIDFromBytes(repositoryPath.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return "repo-" + stableId;
    }
}
