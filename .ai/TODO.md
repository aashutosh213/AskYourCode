# TODO

## Current Sprint

- [ ] Keep backend and frontend project-state files synchronized after every
      meaningful change.
- [x] Implement the frontend repository workflow: enter a local path, submit
      indexing, and display the indexing response and file count.
- [x] Implement the frontend search workflow using keyword, semantic, hybrid,
      and reranked modes.
- [x] Implement the frontend ask workflow using `/api/ask` and render answer
      citations.
- [x] Add a source viewer that opens the cited file and highlights its line
      range.

## Backend Next

- [x] Replace normal-runtime H2 metadata persistence with local PostgreSQL
      configuration and a Flyway schema migration; retain H2 for tests.
- [x] Persist parsing, embedding, storing, completed, and failed stages in
      index jobs.
- [x] Move repository scanning into the job lifecycle so the persisted job
      reports its scanning stage and discovered file count.
- [x] Stop swallowing parser failures; report failed file paths and reasons
      while continuing to inspect remaining files.
- [x] Make chunking a separately reported stage, after parsing and before
      embeddings.
- [x] Review indexing error handling: Qdrant availability/deletion and point
      failures, executor rejection, failure-status persistence, and embedding
      failure details now fail visibly instead of being silently ignored.
- [x] Make re-indexing idempotent by replacing prior files, chunks, and
      embeddings and rebuilding the repository's Qdrant collection.
- [ ] Add file hashes and repository/index versions for incremental indexing.
- [x] Add a repository-scoped source-file endpoint for citation viewing.
- [ ] Add request validation and path-safety tests for all repository and file
      access endpoints.
- [ ] Add integration tests against reachable local PostgreSQL, Qdrant, and
      Ollama services.

## Retrieval and RAG Improvements

- [x] Prevent silent production use of SHA-256 pseudo-vectors: fallback is now
      explicitly opt-in, warns that its vectors are not semantic, and search
      responses report when vector retrieval is unavailable. Tests may opt in
      for offline indexing fixtures.
- [x] Persist embedding model provenance and regenerate existing vectors when
      the configured Ollama model changes or legacy model metadata is missing.
- [ ] Improve and validate the 15-query labelled benchmark for this repository;
      compare vector-only, BM25-only, hybrid, and reranked retrieval using
      Recall@K, Precision@K, MRR, latency, and citation correctness. The
      standalone runner reports the first four; citation scoring and a live
      run against reachable local services remain.
- [ ] Add context-size limits and duplicate-context removal to the context
      builder.
- [ ] Add an optional local cross-encoder reranker behind the existing
      reranking boundary; retain the deterministic baseline as a fallback.
- [ ] Add explicit insufficient-evidence behavior and tests for citation
      grounding in `/api/ask`.

## Later

- [ ] Add search history tracking.
- [ ] Add repository branches and public Git URL ingestion without OAuth.
- [ ] Add incremental commit-aware indexing.
- [ ] Add indexing progress and cancellation support if repository size
      requires it.
- [ ] Add broader language support only after the current Java,
      JavaScript/TypeScript, and Python pipeline is stable.

## Completed

- [x] Create the project root structure and persistent project-memory files.
- [x] Scaffold the Spring Boot backend and health endpoint.
- [x] Initialize the Next.js frontend shell.
- [x] Add local PostgreSQL, Qdrant OSS, and Ollama compose configuration.
- [x] Align the backend with the installed Java 21 and Maven toolchain.
- [x] Implement repository scanning and generated/dependency directory
      filtering.
- [x] Persist repositories, files, chunks, embeddings, and indexing jobs in
      the current H2 development store.
- [x] Implement Java AST parsing and semantic method/constructor chunks.
- [x] Implement JavaScript/TypeScript declaration chunking.
- [x] Implement Python indentation-aware class/function chunking.
- [x] Add chunk pagination and file filtering.
- [x] Add local Ollama embeddings with deterministic fallback behavior.
- [x] Add Qdrant vector storage and semantic search.
- [x] Add Lucene BM25 keyword search.
- [x] Add reciprocal-rank-fusion hybrid retrieval.
- [x] Add deterministic local reranking with provenance flags.
- [x] Add local Ollama answer generation with numbered citations.
- [x] Add retrieval metrics and offline/live benchmark harnesses.
- [x] Add parser, ingestion, retrieval, reranking, and generation tests that
      do not require unavailable external local services.

## Environment-Blocked Verification

- [ ] Run the live Qdrant integration test with a reachable local Qdrant
      service. The restricted sandbox denies socket creation.
- [ ] Reinstall frontend dependencies when npm registry access is available;
      the existing dependency tree and production build are verified.
- [ ] Pull and verify the configured local chat model before declaring live
      `/api/ask` generation verified.
