# Changelog

## 2026-10-02

### Documentation

- Synchronized project memory with the actual backend/frontend state and
  documented the remaining frontend, PostgreSQL, indexing, reliability, and
  verification work.
- Recorded the H2 development-store, Ollama, reranking, parser, and
  provenance decisions in `DECISIONS.md`.

### Added

- Added semantic declaration chunking for TypeScript and JavaScript classes,
  interfaces, types, functions, arrow functions, and methods.
- Added indentation-aware Python class and function chunking.
- Added parser tests covering JavaScript/TypeScript and Python source files.

## 2026-10-02

### Added

- Added local Ollama-backed `POST /api/ask` generation with retrieved context
  and numbered source citations.
- Added `.env.example` documenting local-only configuration without API keys.
- `/api/ask` returns HTTP 503 when the configured local chat model is
  unavailable instead of exposing an internal error.
- Added live Qdrant integration coverage for indexing and vector retrieval.
- Added an explainable local reranking baseline over hybrid candidates.
- Added `POST /api/search/reranked`, preserving retrieval and reranking scores
  plus keyword/vector provenance flags.
- Added unit coverage for exact identifier promotion during reranking.
- Added an offline benchmark harness for comparing retrieval strategies with
  mean Recall@K and reciprocal rank.
- Added a live benchmark integration test covering keyword, vector, hybrid,
  and reranked retrieval against labelled fixture chunks.

### Design

- Reranking uses bounded token overlap, exact phrase, identifier, and RRF
  signals. It requires no hosted API or model download and leaves a seam for a
  local cross-encoder later.

### Fixed

- Qdrant points now use code chunk IDs, allowing correct BM25/vector joins.
- Vector payloads now include chunk content for complete search results.
- Qdrant collection names now derive from the repository path, preventing stale
  vectors from colliding when database-generated repository IDs are reused.
- Qdrant upserts now select only embeddings belonging to the indexed repository,
  preventing cross-repository search results.

## 2026-10-01

### Added

- Added repository-scoped Apache Lucene BM25 keyword search over persisted code chunks.
- Added `POST /api/search/keyword` with chunk provenance, line ranges, and BM25 scores.
- Added integration tests for exact code identifier retrieval and invalid requests.
- Added reciprocal-rank-fusion hybrid search at `POST /api/search/hybrid`.
- Added unit and integration coverage for hybrid retrieval with Qdrant disabled.
- Added offline Recall@K and reciprocal-rank metrics for ranked chunk IDs.

### Verified

- Full backend Maven test suite passes with 11 tests.
- Keyword retrieval works with Qdrant disabled because it uses the local Lucene index.
- Hybrid retrieval falls back to its BM25 candidates when Qdrant is unavailable.
- Retrieval metrics tests pass for hits, misses, and invalid configuration.

### Fixed

- Changed embedding JSON persistence to `@Lob`; 768-dimensional vectors no longer overflow the 10 KB H2 column.

### Verified

- `mvn clean test` passes all 9 backend tests.
- Live Qdrant verification remains pending because Docker/gRPC networking is unavailable in the current environment.

## 2026-08-25

### Added

- Java 21-compatible Maven Surefire configuration for Mockito agent attachment
- A stronger repository ignore filter for nested generated directories and local virtualenv paths
- A regression test covering nested target and .venv paths in the scanner

### Changed

- Updated the project state and architecture docs to reflect verified Phase 1 ingestion work
- Moved the backend configuration to the actual Java 21 environment instead of the previously mismatched Java 25 setup

### Fixed

- Resolved the Java version mismatch that blocked Maven tests under the current environment
- Fixed the failing temp-directory setup for Surefire by pointing it to a valid project build directory
- Restored the backend tests to a clean BUILD SUCCESS state

## 2026-08-16

### Added

- Project foundation restart based on the master prompt requirements
- Local toolchain verification for Java 21, Maven, and Node.js
- Initial project-state metadata files
- Spring Boot health endpoint and backend skeleton
- Repository ingestion controller, request/response DTOs, and scanner skeleton
- Frontend app shell scaffold
- Local infrastructure compose file for PostgreSQL, Qdrant, and Ollama

### Changed

- Project state advanced from empty bootstrapping into Phase 0 completion and Phase 1 ingestion work

### Fixed

- Reconstructed the project state from the actual repository instead of assuming previous session memory
- Added the missing validation starter dependency required for request validation in the ingestion API
