# AskYourCode Architecture

## Current Architecture

### Frontend

The repository contains a Next.js + TypeScript + Tailwind shell. The
developer UI for indexing, search, and asking is not implemented yet because
frontend dependency installation is blocked by the local npm registry policy.

### Backend

The backend is a Java 21 Spring Boot modular monolith. Its ingestion module
scans local repositories, persists file metadata, parses source declarations,
creates semantic code chunks, generates local embeddings, and optionally
pushes vectors to Qdrant. Its search module exposes keyword, vector, hybrid,
and deterministic local reranking endpoints. Its ask module sends retrieved
context to a local Ollama model and returns numbered citations.

### APIs

Current endpoints:

- `GET /api/health`
- `POST /api/repositories/index`
- `GET /api/chunks`
- `POST /api/search/keyword`
- `POST /api/search/vector`
- `POST /api/search/hybrid`
- `POST /api/search/reranked`
- `POST /api/ask`

### Storage and local AI

- H2 currently stores repositories, files, chunks, indexing jobs, and JSON
  embeddings during local development.
- Qdrant OSS stores vectors and provenance payloads when enabled locally.
- Apache Lucene provides repository-scoped BM25 keyword search.
- Ollama is the only model runtime; embeddings and answer generation are
  local and free of hosted API dependencies. A deterministic embedding
  fallback is available when Ollama is unavailable.

### Repository ingestion, parsing, and chunking

The scanner ignores generated/dependency paths and recognizes Java,
JavaScript, TypeScript, and Python. Java uses JavaParser for methods and
constructors. JavaScript/TypeScript use a conservative declaration parser for
classes, interfaces, types, functions, arrow functions, and methods. Python
uses indentation-aware extraction for classes and functions. Every chunk
retains its file path, symbol, symbol type, and exact line range.

### Retrieval and generation

Keyword and vector retrieval run independently. Hybrid retrieval combines
their ranked candidates with reciprocal-rank fusion, then the local
deterministic reranker promotes identifier and phrase matches. The context
passed to Ollama contains only retrieved chunks. The ask response preserves
each chunk's source path and line range as a citation.

### Evaluation

Offline Recall@K, reciprocal rank, and benchmark comparisons are implemented
for keyword, vector, hybrid, and reranked strategies. Live Qdrant benchmark
coverage exists but requires a reachable local Qdrant service.

## Current vs Future

### Current

- Local repository scanning and metadata persistence
- Java, JavaScript, TypeScript, and Python semantic chunking
- Local embedding generation with Qdrant integration and fallback behavior
- BM25, vector, hybrid, and deterministic reranked search
- Local Ollama answer generation with citations
- Retrieval metrics and benchmark harness

### Future

- PostgreSQL as the normal metadata store
- Frontend repository/index/search/ask workflows
- Source viewer with clickable citations
- Async indexing progress and incremental index versioning
- Local model-based cross-encoder reranking
- Search history and broader evaluation datasets
