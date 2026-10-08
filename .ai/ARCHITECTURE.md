# AskYourCode Architecture

## Current Architecture

### Frontend

The repository contains a Next.js + TypeScript + Tailwind developer UI for
entering a local repository path, submitting indexing, selecting keyword,
semantic, hybrid, or reranked retrieval, displaying provenance-preserving
code results, asking the local model for cited answers, and viewing cited
source lines. Next.js rewrites frontend `/api/*` requests to the local Spring
Boot API during development. Live indexing progress remains future work
because the current backend does not expose a durable progress endpoint.

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
- `GET /api/source?repositoryPath=...&fileRelativePath=...&startLine=...&endLine=...`

### Storage and local AI

- PostgreSQL stores repositories, files, chunks, indexing jobs, and JSON
  embeddings with their model key during normal local development. Flyway owns
  the schema.
- File rows store SHA-256 content hashes; repositories store a monotonically
  increasing index version. Indexing reuses unchanged file/chunk/embedding
  records and reparses only changed/new files. Removed paths and their chunks
  and embeddings are deleted. Hashes and the version are committed after the
  indexing pipeline succeeds. Git repositories also record the indexed HEAD
  commit for snapshot provenance; non-Git local directories leave it empty.
- H2 is selected by the test resource configuration for self-contained tests.
- Qdrant OSS stores vectors and provenance payloads when enabled locally.
  Index updates delete point IDs belonging to changed/deleted files and upsert
  embeddings for changed/new files without rebuilding the collection. If a
  collection is missing during an otherwise unchanged index, stored vectors
  repopulate it.
- Apache Lucene provides repository-scoped BM25 keyword search.
- Ollama is the only model runtime; embeddings and answer generation are
  local and free of hosted API dependencies. Deterministic SHA-256 placeholder
  embeddings are opt-in for tests and must not be treated as semantic vectors.
  When query embeddings are unavailable, vector search reports a warning and
  hybrid search retains BM25 results.

### Repository ingestion, parsing, and chunking

The scanner ignores generated/dependency paths and recognizes Java,
JavaScript, TypeScript, and Python. Java uses JavaParser for methods and
constructors. JavaScript/TypeScript use a conservative declaration parser for
classes, interfaces, types, functions, arrow functions, and methods. Python
uses indentation-aware extraction for classes and functions. Every chunk
retains its file path, symbol, symbol type, and exact line range.
The parser returns structured declarations; a separate chunking service
persists those declarations as retrieval chunks. The indexing job records
SCANNING, PARSING, CHUNKING, EMBEDDING, and STORING as separate stages.

### Retrieval and generation

Keyword and vector retrieval run independently. Hybrid retrieval combines
their ranked candidates with reciprocal-rank fusion, then the local
deterministic reranker promotes identifier and phrase matches. `/api/ask`
selects distinct whole chunks in retrieval order within the configurable
`ASK_CONTEXT_MAX_CHARACTERS` budget (12,000 characters by default). It skips
chunks that do not fit, preserves source metadata, and numbers citations only
for selected chunks before sending context to Ollama. The budget is a character
cap rather than a tokenizer-specific token count. Explicit insufficient-
evidence handling returns a deterministic response without a model call when
no usable chunks fit. Generated numeric citation markers are checked against
the selected source set, and unsupported markers are labeled unverified.

### Evaluation

Offline Recall@K, Precision@K, reciprocal rank, average latency, and benchmark
comparisons are implemented for keyword, vector, hybrid, and reranked
strategies. A 15-query labelled dataset and standalone runner cover this
repository's code. Citation correctness and live Qdrant evaluation remain
future work.

## Current vs Future

### Current

- Local repository scanning and metadata persistence
- Java, JavaScript, TypeScript, and Python semantic chunking
- Local embedding generation with Qdrant integration and fallback behavior
- BM25, vector, hybrid, and deterministic reranked search
- Local Ollama answer generation with citations
- Bounded, de-duplicated ask context with provenance-preserving citations
- Deterministic insufficient-evidence response when no context is usable
- Retrieval metrics and benchmark harness

### Future

- Async indexing cancellation if repository size requires it
- Local model-based cross-encoder reranking
- Citation-correctness evaluation and live local-stack verification
- Search history and broader evaluation datasets
