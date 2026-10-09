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
- `/api/ask` selects distinct, provenance-preserving chunks within a configurable
  character budget and numbers citations only for selected context.
- Local Ollama `/api/ask` generation with numbered source citations.
- Offline retrieval metrics for Recall@K, Precision@K, MRR, and average
  per-query latency.
- Backend unit/integration coverage that does not require unavailable external
  local services. Last full `mvn test` run: 44 tests, 0 failures, 0 errors; the
  two live-Qdrant classes skip when Qdrant is not listening on `localhost:6333`.

## Partially Implemented

- Frontend Next.js/TypeScript/Tailwind repository, indexing, search, ask, and
  citation source-viewer workflows are implemented.
- PostgreSQL with Flyway is the normal local metadata store; H2 is used by the
  self-contained test configuration.
- Indexing runs asynchronously and persists scanning, parsing, chunking,
  embedding, storage, completion, and failure stages, plus parser failure
  details.
- Index retries and forced re-indexing replace repository metadata after
  scanning succeeds when changes exist. Normal
  re-indexing hashes files, reuses unchanged file/chunk/embedding records,
  reparses only new or changed files, and reconciles only the affected Qdrant
  point IDs. Git repositories record the indexed HEAD commit.
- The deterministic reranker is a baseline; a local cross-encoder is future
  work.

## Broken

- No known core backend compilation or unit-test defect. The previous
  indexing/search test failures came from tests asserting on queued work before
  the background job finished; they now wait for job completion.
- Full live Qdrant verification cannot run in the restricted sandbox because
  socket creation is denied.
- Frontend dependency installation remains blocked by the local npm registry
  policy (403 from registry.npmjs.org); the existing dependency tree builds
  successfully.

## Current Task

Validate repository and source access requests consistently (malformed paths,
invalid limits, canonical-path and symlink containment) and cover it with
endpoint tests. See `.ai/TODO.md` for acceptance details.

## Last Completed Task

- Retrieval quality changes (planned and implemented on 2026-10-10):
  - Java class, interface, enum, and record header chunks; `parent_symbol`
    persisted for Java members and Python nested definitions (Flyway V7).
  - Enriched `search_document:` embedding text with file path and qualified
    symbol; `search_query:` prefix for queries; model key carries
    `enriched-v2`, so existing vectors are regenerated on re-index.
  - Indexing pushes regenerated vectors for unchanged files to Qdrant.
  - Question/function words removed from BM25 and reranking query terms;
    identifier overlap is a graded token ratio.
  - Java parsing fix: JavaParser upgraded to 3.28.2 so repositories using
    Java 21 syntax index correctly; syntax errors now fail the job with messages.
  - Oversized symbols are split into line windows of at most 3,000 characters
    (the embedding model rejects inputs over 2048 tokens); embedded text is capped
    at 4,000 characters; expression-bodied arrows end at their body.
  - Backend tests: 44 tests, 0 failures (28 before). Test contexts now use a
    unique in-memory H2 database to avoid cross-context ID collisions.
  - Not measured: Ollama, Qdrant, and PostgreSQL were not reachable, so the
    labelled benchmark has not been run before or after these changes.

- Fixed the failing backend test suite. Indexing, reranked search, and chunk
  tests now wait for their background job (`IndexingTestSupport` in
  `backend/src/test/java/com/askyourcode/app/`) before asserting, and the
  reranked assertions read the completed `SearchJob.result`. The two live
  Qdrant test classes skip when Qdrant is unreachable. Result: 28 tests pass.

- `/api/ask` now returns a deterministic insufficient-evidence result without
  calling Ollama when no usable chunks fit. The response exposes an
  `insufficientEvidence` flag, the frontend explains the state, and generated
  citation markers outside the selected source set are replaced with an
  unverified marker. Backend compilation passed; focused behavior tests remain
  outstanding.
- Added a bounded ask-context builder with a configurable character cap,
  duplicate-chunk removal, whole-chunk selection, stable retrieval ordering,
  and citation numbering based only on selected chunks. Backend compilation
  passed; behavior tests remain part of the follow-up verification.
- Added SHA-256 content hashes to file metadata and a monotonically increasing
  repository index version. Re-indexing now keeps unchanged file/chunk records,
  reparses new/changed files, removes deleted files and dependent records, and
  reconciles Qdrant point IDs for changed/deleted files while upserting only
  changed-file vectors. Git HEAD is stored as snapshot provenance. Hashes,
  version, and commit are committed transactionally after successful indexing,
  so interrupted files are retried. A missing collection is repopulated from
  stored vectors.
- Tightened indexing failure reporting: executor rejection and failure-status
  persistence errors are handled, Qdrant connectivity errors are no longer
  mistaken for a missing collection, interrupted Qdrant calls restore the
  interrupt flag, and embedding/vector failures include file-level details.
- Split parser output from chunk persistence: source parsing returns structured
  declarations, then a dedicated chunking service persists them under a
  separately recorded CHUNKING stage before embedding.
- Made indexing retries and forced re-indexes idempotent by clearing prior
  embeddings, chunks, and files, then rebuilding the repository's Qdrant
  collection from the fresh scan. Qdrant point storage failures now fail the
  indexing job instead of being reported as successful.
- Moved repository discovery into the asynchronous indexing job lifecycle and
  persistently report SCANNING plus the discovered file list/count. Forced
  re-indexing clears prior records only after scanning succeeds.
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

Start Ollama, Qdrant, and PostgreSQL. Run the labelled benchmark at commit
`8689c4a` (baseline) and at the current commit, both after re-indexing, and
record Recall@K, Precision@K, MRR, and latency in `.ai/evaluation`. Then finish
the repository/source access validation task.

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
  Ask context is de-duplicated and bounded by `ASK_CONTEXT_MAX_CHARACTERS`
  (default 12,000); the cap counts characters, not tokenizer-specific tokens.
  If no usable chunk fits, `/api/ask` returns an insufficient-evidence result
  without calling Ollama. Unsupported generated citation markers are labeled
  unverified.

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

- Retrieval changes from 2026-10-10 are unmeasured against live services.
  Re-index each repository after upgrading: stored vectors without the
  `enriched-v2` model key are regenerated, and query prefixes only match
  re-indexed vectors. Compare against the pre-change commit `8689c4a` with the
  labelled benchmark.
- The Qdrant push for files whose embeddings were regenerated without a file
  change has no automated test; it needs live Qdrant and Ollama to verify.
- JavaScript/TypeScript chunks have no `parent_symbol` yet.
- A single Java file with a syntax error still fails the whole indexing job (the
  existing behaviour). Skipping just that file with a warning is a possible follow-up.
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
- Git HEAD is recorded for provenance, while file content hashes remain the
  correctness check so dirty working-tree edits are indexed too.
- Test helpers wait on background jobs; any new test that indexes or searches
  must use `IndexingTestSupport` rather than assert right after the POST.
- Live Qdrant tests are skipped, not run, without a local Qdrant on
  `localhost:6333`; a skipped run is not evidence of live Qdrant behavior.

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

- `backend/src/main/resources/db/migration/V7__add_chunk_parent_symbol.sql` (new)
- `backend/src/main/java/com/askyourcode/app/ingestion/CodeParserService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/ParsedCodeSymbol.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/CodeChunkingService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/model/CodeChunkEntity.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/RepositoryIndexingService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/embedding/EmbeddingText.java` (new)
- `backend/src/main/java/com/askyourcode/app/ingestion/embedding/EmbeddingService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/embedding/LocalEmbeddingService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/search/CodeSearchText.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/search/KeywordSearchService.java`
- `backend/src/main/java/com/askyourcode/app/ingestion/search/RerankingService.java`
- `backend/src/test/resources/application.properties`
- New and updated tests: `CodeParserParentTest`, `EmbeddingTextTest`,
  `CodeSearchTextTest`, `RerankingServiceTest`, `CodeChunkByFileTest`,
  `CodeChunkControllerTest`
- `.ai/ARCHITECTURE.md`, `.ai/DECISIONS.md`, `.ai/CHANGELOG.md`, `.ai/TODO.md`

- `backend/src/test/java/com/askyourcode/app/IndexingTestSupport.java` (new)
- `backend/src/test/java/com/askyourcode/app/RepositoryIngestionControllerTest.java`
- `backend/src/test/java/com/askyourcode/app/HybridSearchIntegrationTest.java`
- `backend/src/test/java/com/askyourcode/app/EmbeddingIntegrationTest.java`
- `backend/src/test/java/com/askyourcode/app/VectorSearchIntegrationTest.java`
- `backend/src/test/java/com/askyourcode/app/CodeChunkControllerTest.java`
- `backend/src/test/java/com/askyourcode/app/CodeChunkByFileTest.java`
- `backend/src/test/java/com/askyourcode/app/LiveQdrantIntegrationTest.java`
- `backend/src/test/java/com/askyourcode/app/LiveRetrievalBenchmarkIntegrationTest.java`
- `.ai/TODO.md`
- `.ai/PROJECT_STATE.md`
- `.ai/CHANGELOG.md`
