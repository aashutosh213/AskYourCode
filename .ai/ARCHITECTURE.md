# AskYourCode Architecture

## Current Architecture

### Frontend

A future Next.js + TypeScript + Tailwind frontend will provide the developer-oriented repository search interface. The frontend shell is scaffolded, but dependency installation remains blocked by the local npm registry policy.

### Backend

The backend is a Java 21 Spring Boot modular monolith. The current verified implementation includes the application shell, a health endpoint, and the initial repository ingestion flow.

### APIs

Current API surface:

- GET /api/health
- POST /api/repositories/index

Planned API surface:

- POST /api/repositories
- POST /api/repositories/{id}/index
- POST /api/search
- POST /api/ask

### PostgreSQL

PostgreSQL will store repository metadata, file metadata, indexing jobs, and search history. This is not yet persisted in the application, but it is part of the intended Phase 1-to-Phase 4 design.

### Qdrant

Qdrant OSS will eventually store embeddings and metadata for vector similarity retrieval. It is not yet wired into the application.

### Repository Ingestion

The current implementation scans a local repository directory for candidate source files, filters generated directories and nested dependency paths, and returns a simple indexed status response. This is the Phase 1 foundation that prepares for later metadata persistence and chunk generation.

### Parsing and Chunking

Not yet implemented. The design is to parse code into symbol-aware structures and generate semantic chunks.

### Embedding and Retrieval

Not yet implemented. The architecture will eventually separate semantic, keyword, and hybrid retrieval.

### Reranking and Generation

Not yet implemented. Current architecture keeps retrieval independent from generation and remains local-first.

### Citations and Evaluation

Planned for the later phases. The final system will cite exact file and line ranges grounded in retrieved chunks.

## Current vs Future

### Current

- Local-only development default
- Java 21 + Spring Boot foundation
- Verified health endpoint
- Repository scanner and ingestion endpoint for local repository discovery
- Java 21/Maven configuration aligned to the actual environment

### Future

- Persistent file metadata and indexing jobs
- AST parsing and chunking
- Local embeddings and Qdrant search
- BM25 keyword search
- Hybrid retrieval and reranking
- Local Ollama generation
- Citations and evaluation

