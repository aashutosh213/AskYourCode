# AskYourCode Project State

## Current Phase

Phase 3: Retrieval Evaluation (IN PROGRESS)

## Overall Progress

- Phase 0: COMPLETED
- Phase 1: COMPLETED
- Phase 2: COMPLETED
- Phase 3: IN PROGRESS
- Phase 4: NOT STARTED
- Phase 5: NOT STARTED
- Phase 6: NOT STARTED
- Phase 7: NOT STARTED
- Phase 8: NOT STARTED
- Phase 9: NOT STARTED
- Phase 10: NOT STARTED
- Phase 11: NOT STARTED
- Phase 12: NOT STARTED

## What Works

- Backend foundation is running and health endpoint is verified.
- Repository ingestion API is implemented and tested for the local repository scanning flow.
- RepositoryScanner correctly ignores generated and dependency directories (including nested target and .venv paths) and collects Java/JS/TS/Python source files.
- Java 21 toolchain is aligned to the local environment, and the Maven tests pass under the installed JDK.
- Local-only project structure is established and documented.
- File metadata persistence and indexing job state are persisted to an embedded H2 database during indexing.
- Java parsing and semantic chunking with javaparser creates code chunks for methods and constructors.
- Chunk retrieval API with pagination and file filtering.
- Embedding generation via Ollama (with pseudo-embedding fallback).
- Embeddings persisted to H2 database as JSON.
- Qdrant integration with official Java client (gRPC-based).
- Vector search API endpoint for semantic code search.

## Partially Implemented

- Frontend shell files are scaffolded, but dependency installation is blocked by the local npm registry policy.
- Vector search requires Qdrant to be running and enabled (qdrant.enabled=true).
- End-to-end Qdrant verification is pending because Docker/gRPC networking is unavailable in the current sandbox.

## Broken

- Frontend dependency installation remains blocked by npm registry access errors in this environment.

## Current Task

Phase 2 completed: embeddings and Qdrant integration. Phase 3 retrieval is implemented with BM25 and RRF hybrid fusion; offline Recall@K and reciprocal-rank evaluation are now available.

## Last Completed Task

- Fixed embedding persistence for full 768-dimensional JSON vectors by changing the H2 column mapping to a large object.
- Ran `mvn clean test`: all 9 tests passed.
- Added Qdrant Java client dependency (io.qdrant:client:1.9.1)
- Created QdrantConfig for conditional Qdrant client bean
- Rewrote QdrantEmbeddingClient using official Qdrant Java client with proper collection lifecycle management
- Enhanced LocalEmbeddingService with progress logging, statistics, and embedText() method for single-text embeddings
- Created VectorSearchService to query Qdrant and return ranked results
- Created VectorSearchController with POST /api/search/vector endpoint
- Added repository-scoped Lucene BM25 keyword search with stable source metadata
- Added KeywordSearchController with POST /api/search/keyword endpoint
- Added reciprocal-rank-fusion hybrid retrieval with POST /api/search/hybrid
- Added hybrid provenance flags showing whether each result came from BM25 and/or vector search
- Added offline RetrievalMetrics for Recall@K and reciprocal rank evaluation
- Added VectorSearchIntegrationTest with Qdrant disabled scenario
- All embedding and vector search infrastructure is in place

## Next Recommended Task

Implement the next Phase 3 slice:
1. Validate the live Qdrant path when Docker is available.
2. Use a benchmark dataset to compare keyword, vector, and hybrid behavior.

## Current Architecture

- Frontend: Next.js + TypeScript + Tailwind shell scaffolded
- Backend: Java 21 + Spring Boot + Maven with health, ingestion, chunks, and vector search endpoints
- Infrastructure: local PostgreSQL + Qdrant OSS + Ollama via Docker/Podman Compose
- Storage: H2 for metadata and embeddings (JSON), Qdrant for vector storage
- AI runtime: local Ollama only; no paid APIs or cloud services
- Ingestion flow: repository path validation -> repository scanner -> candidate file filtering -> parsing -> chunking -> embedding generation -> Qdrant push
- Search flow: query -> embed query -> Qdrant vector search -> ranked results
- Keyword search flow: query -> repository-scoped in-memory Lucene index -> BM25 ranked results
- Evaluation flow: expected chunk ids + ranked results -> Recall@K and reciprocal rank metrics

## Important Technical Details

- Free-only default is enforced.
- Retrieval and generation remain intentionally separate.
- The backend is a modular monolith with layered ingestion and search.
- Qdrant client uses gRPC for performance.
- Lucene keyword indexing is rebuilt per repository search for this initial slice, keeping results aligned with persisted chunks while indexing versioning is not yet implemented.
- Collection naming: `repo-{repositoryId}` for multi-repository isolation.
- Vector dimension: 768 (nomic-embed-text model).
- Embeddings stored both in H2 (as JSON) and Qdrant (as vectors with metadata).
- Graceful fallback when Ollama unavailable (pseudo-embeddings using SHA-256).
- Graceful handling when Qdrant disabled (embeddings stored in H2 only).

## Known Problems

- Frontend npm install is blocked by a registry access error (403 from registry.npmjs.org). This is an environment issue, not a code issue.

## Important Decisions

- Use a local, free-only architecture.
- Keep the backend modular but simple.
- Use official Qdrant Java client instead of manual REST calls.
- Store embeddings in both H2 (persistence) and Qdrant (search).
- Separate collection per repository for isolation.
- Match the project configuration to the Java 21 environment instead of assuming a newer JDK is installed.

## Development Commands

- Java 21: available locally
- Maven: available locally
- Node.js: available locally
- Backend compile: `mvn clean compile`
- Backend test: `mvn test`
- Backend run: `mvn spring-boot:run`
- Frontend install/build: currently blocked by registry access rules
- Infrastructure: `cd infrastructure && docker-compose up -d`
- Pull Ollama model: `docker exec -it askyourcode-ollama ollama pull nomic-embed-text`

## Environment Requirements

- Java 21
- Maven
- Node.js 20+
- Docker Engine/Compose or Podman/Compose
- PostgreSQL local (planned, not yet used)
- Qdrant OSS local
- Ollama local with nomic-embed-text model

## New API Endpoints

- POST /api/search/vector - Vector search for code chunks by semantic similarity
  - Request: `{"query": "text", "repositoryPath": "/path", "limit": 10}`
  - Response: `{"results": [...], "query": "text", "resultsCount": N}`
- POST /api/search/keyword - BM25 keyword search for persisted code chunks
  - Request: `{"query": "JwtAuthenticationFilter", "repositoryPath": "/path", "limit": 10}`
  - Response includes chunk id, file path, symbol, line range, content, and BM25 score
- POST /api/search/hybrid - RRF fusion of keyword and vector candidates
  - Response includes fused score plus `keywordMatch` and `vectorMatch` flags

## Files Modified/Created

### Modified:
- backend/pom.xml - Added Qdrant client and protobuf dependencies
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/LocalEmbeddingService.java - Added progress logging, stats, embedText() method
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/QdrantEmbeddingClient.java - Complete rewrite with official client

### Created:
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/QdrantConfig.java - Spring configuration for QdrantClient bean
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/VectorSearchService.java - Vector search service
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/VectorSearchController.java - Vector search REST endpoint
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/VectorSearchRequest.java - Request DTO
- backend/src/main/java/com/askyourcode/app/ingestion/embedding/VectorSearchResult.java - Response DTO
- backend/src/test/java/com/askyourcode/app/VectorSearchIntegrationTest.java - Integration tests
