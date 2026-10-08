# TODO

## Current Sprint

- [x] Add a bounded context builder for `/api/ask`: configure a prompt-context
      budget, remove duplicate chunks, keep each selected chunk intact, and
      assign citation numbers only after selection. Preserve the existing
      retrieval order and source metadata.
- [x] Make `/api/ask` handle insufficient evidence explicitly: return an
      evidence-based response without calling Ollama when retrieval has no
      usable chunks, and replace model-generated citation references that do
      not map to selected context.
- [x] Add focused automated coverage for empty/oversized context, duplicate
      selection, the no-Ollama insufficient-evidence path, and valid versus
      invalid generated citation references.
- [ ] Validate repository and source access requests consistently. Reject
      malformed paths and invalid limits, enforce canonical-path and symlink
      containment for local repository/file reads, and cover the behavior with
      endpoint tests.

## Next

- [ ] Extend the labelled retrieval evaluation with citation correctness and
      run keyword, vector, hybrid, and reranked modes against reachable local
      services. Save a reproducible report with the dataset/model settings and
      Recall@K, Precision@K, MRR, latency, and citation results.
- [ ] Verify the normal local stack end to end: PostgreSQL migrations and
      persistence, Qdrant incremental point reconciliation/recovery, and Ollama
      embedding plus answer generation. Record commands and outcomes, and
      distinguish environment failures from application failures.
- [ ] Add an optional local cross-encoder reranker behind the existing
      reranking interface only after recording the deterministic baseline.
      Compare ranking quality and latency on the labelled dataset; retain the
      deterministic reranker as the offline fallback.
- [ ] Expose indexing job progress in the frontend by polling the existing job
      status and showing stage, discovered-file count, completion, and failure
      details. Keep the UI usable while indexing runs.

## Later

- [ ] Add indexing cancellation only if measured repository size or user
      feedback makes it necessary.
- [ ] Add search history after the core ask and evaluation workflows are
      reliable.
- [ ] Consider public Git URL/branch indexing and broader language support
      after local repository indexing and incremental updates are stable.

## Completed

- [x] Fix the failing backend test suite: wait for background indexing and
      reranked-search jobs before asserting (shared `IndexingTestSupport`),
      update reranked assertions to the job result shape, and skip live Qdrant
      tests when Qdrant is unreachable. `mvn test` passes with 28 tests.
- [x] Return a deterministic insufficient-evidence answer without calling
      Ollama when no usable chunks fit the ask context; expose the state in the
      API response and frontend, and mark unsupported citation references.
- [x] Add and run focused ask-context/evidence tests for oversized and duplicate
      chunks, empty evidence, weak candidates, and citation validation.
- [x] Bound and de-duplicate `/api/ask` context by configurable character
      budget, preserve whole chunks and source metadata, and number citations
      only for selected evidence.
- [x] Create the project structure, persistent project-memory files, Spring
      Boot health endpoint, and Next.js frontend shell.
- [x] Add local PostgreSQL, Qdrant OSS, and Ollama compose configuration.
- [x] Implement repository scanning with generated/dependency filtering,
      Java parsing, JavaScript/TypeScript declaration parsing, and Python
      indentation-aware parsing.
- [x] Persist repositories, files, chunks, embeddings, and indexing jobs in
      PostgreSQL with Flyway; retain H2 for self-contained tests.
- [x] Add semantic chunks with source provenance and paginated/file-filtered
      chunk retrieval.
- [x] Add local Ollama embeddings, Qdrant vector storage/search, Lucene BM25,
      reciprocal-rank-fusion hybrid retrieval, and deterministic reranking.
- [x] Add local Ollama answer generation, numbered source citations, and the
      frontend repository, search, ask, and citation source-viewer workflows.
- [x] Persist indexing stages and parser failures; make indexing retries
      visible and idempotent.
- [x] Add SHA-256 file reconciliation, repository index versions, indexed Git
      HEAD provenance, and per-file Qdrant point updates with collection
      recovery from persisted vectors.
- [x] Add retrieval metrics and a labelled 15-query benchmark runner for
      Recall@K, Precision@K, MRR, and average latency.
- [x] Add backend tests for parsing, ingestion, retrieval, reranking,
      generation, citations, and source-path safety.

## Environment-Blocked Verification

- [ ] Run live Qdrant integration when a local Qdrant service is reachable; the
      live Qdrant test classes (`LiveQdrantIntegrationTest`,
      `LiveRetrievalBenchmarkIntegrationTest`) currently skip without it.
- [ ] Run PostgreSQL migration/integration checks when Docker/Podman is
      reachable; PostgreSQL startup has not been live-verified here.
- [ ] Reinstall frontend dependencies when npm registry access is available;
      registry access currently returns 403, while the existing dependency
      tree has built successfully.
- [ ] Pull the configured local chat model and verify live `/api/ask` when
      Ollama and the model are available.
