# AskYourCode Project State

## Current Phase

Phase 5 frontend integration and backend reliability/polish. The frontend
repository/index/search/ask/source-viewer workflow is now implemented against
the current backend APIs; backend persistence/reliability work remains.

## Overall Progress

- Phase 0: COMPLETED
- Phase 1: COMPLETED
- Phase 2: COMPLETED
- Phase 3: COMPLETED
- Phase 4: COMPLETED
- Phase 5: PARTIALLY COMPLETED (frontend workflow implemented; backend polish pending)
- Phase 6: COMPLETED
- Phase 7: COMPLETED
- Phase 8: COMPLETED
- Phase 9: COMPLETED
- Phase 10: COMPLETED
- Phase 11: COMPLETED
- Phase 12: NOT STARTED

## What Works

- Spring Boot health and local repository indexing endpoints.
- Repository scanning with generated/dependency directory filtering.
- File, chunk, embedding, and indexing-job persistence in local PostgreSQL;
  H2 remains available for self-contained tests.
- Embedding records retain a model key; re-indexing refreshes vectors whose
  model key differs from the configured Ollama model.
- Java AST parsing with JavaParser for methods and constructors.
- Conservative semantic declaration chunking for JavaScript, TypeScript, and
  Python.
- Chunk retrieval with pagination and file filtering.
- Local Ollama embedding integration; deterministic SHA-256 placeholder
  embeddings are disabled by default and opt-in for tests.
- Qdrant OSS indexing and vector search when Qdrant is reachable and enabled.
- Lucene BM25 keyword search.
- Reciprocal-rank-fusion hybrid retrieval.
- Explainable deterministic local reranking.
- Local Ollama `/api/ask` generation with numbered source citations.
- Offline retrieval metrics for Recall@K, Precision@K, MRR, and average
  per-query latency.
- Backend unit/integration coverage that does not require unavailable external
  local services.

## Partially Implemented

- Frontend Next.js/TypeScript/Tailwind repository, indexing, search, ask, and
  citation source-viewer workflows are implemented.
- PostgreSQL with Flyway is the normal local metadata store; H2 is used by the
  self-contained test configuration.
- Indexing runs asynchronously and now persists job stages and parser failure
  details; chunking is currently part of the parsing stage.
- Re-indexing does not yet perform complete stale-file, stale-chunk, or stale
  vector cleanup.
- The deterministic reranker is a baseline; a local cross-encoder is future
  work.

## Broken

- No known core backend compilation or unit-test defect.
- Full live Qdrant verification cannot run in the restricted sandbox because
  socket creation is denied.
- Frontend dependency installation remains blocked by the local npm registry
  policy (403 from registry.npmjs.org); the existing dependency tree builds
  successfully.

## Current Task

Improve and measure retrieval accuracy. Identifier-aware normalization and
explicit handling of unavailable vector embeddings are implemented. A
15-query labelled dataset and standalone comparison runner are in place; next,
validate the labels and assess retrieval/citation quality with reachable local
services.

## Last Completed Task

- Added a standalone labelled source-search benchmark with 15 queries and
  comparison of keyword, vector, hybrid, and reranked modes.
- Extended the benchmark runner to report Precision@K and average query
  latency alongside Recall@K and MRR.
- Persisted model keys for generated embeddings and regenerate vectors when
  re-indexing detects a configured-model mismatch or legacy record.
- Normalized BM25 and reranking text for case-insensitive camelCase and
  PascalCase matching.
- Disabled placeholder embeddings by default; made failed embedding generation
  fail indexing and surfaced unavailable vector retrieval in search responses.
- Persisted indexing job stages and surfaced per-file parser failures while
  continuing to inspect the remaining files.
- Implemented the frontend repository indexing, search-mode, local ask, result,
  citation, error, and backend-proxy workflows.
- Added a repository-scoped, path-safe source endpoint and citation viewer with
  highlighted line ranges.
- Added JavaScript/TypeScript declaration parsing for classes, interfaces,
  types, functions, arrow functions, and methods.
- Added Python indentation-aware class and function parsing.
- Added parser tests for brace-based and indentation-based languages.
- Updated architecture, TODO, and decision records to reflect the real
  implementation and remaining work.

## Next Recommended Task

The next backend task should make indexing stages durable and re-indexing
idempotent now that PostgreSQL is the normal metadata store.

## Current Architecture

- Frontend: Next.js + TypeScript + Tailwind workflow UI for indexing, search,
  local ask, result evidence, citations, and highlighted source viewing. Next rewrites proxy `/api/*` to
  the local Spring Boot service during development.
- Backend: Java 21 + Spring Boot modular monolith.
- Metadata: local PostgreSQL with Flyway migrations; H2 for tests.
- Vector store: local Qdrant OSS.
- Keyword search: Apache Lucene BM25.
- Embeddings and generation: local Ollama only. Placeholder embedding fallback
  is opt-in; hybrid search reports unavailable vector retrieval and retains
  keyword candidates.
- Ingestion: path validation -> scan -> language detection -> parse ->
  semantic chunks -> embeddings -> Qdrant.
- Retrieval: BM25 and vector search independently -> RRF hybrid fusion ->
  deterministic reranking.
- Generation: retrieved context -> local Ollama -> answer plus citations.

## Important Technical Details

- Supported source languages: Java, JavaScript, TypeScript, and Python.
- Java uses JavaParser; the other languages use a conservative local parser
  that does not execute repository code.
- Qdrant collection names are derived from repository paths for isolation.
- Embedding dimension is 768 for `nomic-embed-text`.
- Embeddings are stored as JSON with a model key in PostgreSQL and as vectors
  with source provenance payloads in Qdrant.
- Lucene indexes are rebuilt per repository search in the current design.
- Retrieval and generation remain separate so retrieval can be evaluated
  without an LLM.
- No paid API, hosted model, hosted vector database, or required API key is
  part of the architecture.

## Known Problems

- Search quality has not yet been measured on the new 15-query labelled
  repository benchmark. The runner does not yet measure citation correctness,
  and live Qdrant/Ollama comparisons remain environment-dependent.
- SHA-256 pseudo-embeddings are disabled by default and can only be enabled
  explicitly with `OLLAMA_EMBEDDING_FALLBACK_ENABLED=true`; they are for
  deterministic tests and do not provide semantic similarity. Re-indexing
  replaces legacy or mismatched embedding records when the Ollama model is
  available.
- Keyword retrieval previously treated code identifiers as case-sensitive
  whitespace tokens, so query casing and camelCase boundaries could miss
  relevant chunks. Search normalization now lowercases and splits identifier
  boundaries for BM25 and deterministic reranking.
- The configured local chat model must be pulled before live `/api/ask`
  generation can be verified.
- Qdrant live integration requires a reachable local Qdrant service.
- PostgreSQL Flyway startup has not been live-verified because Docker is not
  reachable in the current sandbox.
- Frontend npm installation is blocked by the current registry policy.
- Idempotent re-indexing and incremental indexing are not implemented yet.

## Important Decisions

- Free/local-only architecture.
- Modular monolith instead of microservices.
- Qdrant OSS for vectors and PostgreSQL for normal local metadata storage.
- H2 used for self-contained development tests.
- Ollama is the local embedding/generation adapter.
- Retrieval remains independent from generation.
- Deterministic reranking is the current no-model baseline.
- JavaParser plus conservative dependency-free parsers for the supported
  languages.
- Provenance is preserved through retrieval and generation for citations.

## Development Commands

- Backend compile: `cd backend && mvn clean compile`
- Focused parser test: `cd backend && mvn -q -Dtest=CodeParserServiceTest test`
- Backend tests: `cd backend && mvn test`
- Backend run: `cd backend && mvn spring-boot:run`
- Infrastructure: `cd infrastructure && docker-compose up -d`
- Embedding model: `docker exec -it askyourcode-ollama ollama pull nomic-embed-text`
- Chat model: pull the configured `ollama.chat.model` locally before `/api/ask`.

## Environment Requirements

- Java 21
- Maven
- Node.js 20+
- Docker Engine/Compose or Podman/Compose
- PostgreSQL local (normal metadata store)
- Qdrant OSS local
- Ollama local
- `nomic-embed-text` for embeddings
- A locally downloaded open-source Ollama chat model

## Current API Endpoints

- `GET /api/health`
- `POST /api/repositories/index`
- `GET /api/chunks`
- `POST /api/search/keyword`
- `POST /api/search/vector`
- `POST /api/search/hybrid`
- `POST /api/search/reranked`
- `POST /api/ask`
- `GET /api/source`

## Files Changed in the Most Recent Task

- `backend/src/main/java/com/askyourcode/app/ingestion/CodeParserService.java`
- `backend/src/test/java/com/askyourcode/app/ingestion/CodeParserServiceTest.java`
- `.ai/ARCHITECTURE.md`
- `.ai/TODO.md`
- `.ai/DECISIONS.md`
- `.ai/PROJECT_STATE.md`
