## Decision

Use a free/local-only architecture by default.

## Reason

The project requirement explicitly prohibits paid APIs, hosted vector databases, and cloud inference. Local-first design leads to a healthier portfolio project and keeps the stack understandable.

## Alternatives

- Paid OpenAI/Anthropic APIs
- Hosted Qdrant or Pinecone
- Cloud PostgreSQL

## Tradeoff

The local setup is slightly more work than a cloud prototype, but it is compatible with the zero-cost development requirement and better matches the project goals.

## Decision

Use a modular monolith for the backend.

## Reason

The project is intentionally simple at first and should stay easy to reason about. A single Spring Boot app can contain ingestion, retrieval, and generation modules without introducing unnecessary complexity.

## Alternatives

- Microservices
- Event-driven system
- Split repositories for each stage

## Tradeoff

The architecture is less horizontally scalable than a distributed system, but it is much easier to understand, debug, and demonstrate in a portfolio.

## Decision

Use Qdrant OSS and PostgreSQL locally.

## Reason

PostgreSQL handles metadata and repository state while Qdrant handles vector search. This split reflects the project’s intended architecture and keeps retrieval and metadata concerns separate.

## Alternatives

- PostgreSQL-only with pgvector
- Chroma or Weaviate
- Cloud-hosted vector DBs

## Tradeoff

Another local service adds operational work, but it cleanly matches the intended retrieval architecture and is still free to run.

## Decision

Use H2 as the current development metadata store while keeping PostgreSQL as
the target local application database.

## Reason

H2 keeps backend tests and early local development self-contained. The master
plan still requires PostgreSQL for the normal local deployment, so H2 is an
explicit development stage rather than a replacement for PostgreSQL.

## Alternatives

- Require PostgreSQL for every unit/integration test
- Use PostgreSQL immediately without migrations
- Use SQLite

## Tradeoff

Development is easier to start, but H2/PostgreSQL differences must be tested
and the PostgreSQL migration remains unfinished.

## Decision

Keep retrieval and generation separate, and use deterministic local
reranking as the current baseline.

## Reason

Retrieval can be evaluated without an LLM, and the project must remain
usable when a local model is unavailable. The baseline makes ranking signals
and failure behavior explainable.

## Alternatives

- Couple search directly to answer generation
- Require a local cross-encoder immediately
- Use a hosted reranking API

## Tradeoff

The baseline is cheaper and easier to debug but may be less accurate than a
local cross-encoder; a local model can be added behind the same boundary
later.

## Decision

Use Ollama as the local adapter for embeddings and answer generation.

## Reason

It satisfies the free-only, source-private, offline-first requirements and
allows the model choice to change through configuration.

## Alternatives

- Local Sentence Transformers or ONNX Runtime
- Hosted embedding or LLM APIs
- A cloud vector or generation service

## Tradeoff

Local inference uses the developer's CPU/RAM/VRAM and requires model files,
but it avoids API costs, credentials, and source-code disclosure.

## Decision

Use JavaParser for Java and a conservative dependency-free declaration parser
for JavaScript, TypeScript, and Python in the current ingestion slice.

## Reason

JavaParser provides reliable Java AST symbols. The other supported languages
can be chunked without executing repository code or adding another parser
runtime, which keeps the modular monolith understandable.

## Alternatives

- Tree-sitter bindings for every supported language
- Regex-only parsing for Java as well
- Treat all files as fixed-size text chunks

## Tradeoff

The current parser is lightweight and safe but is not a complete grammar.
Tree-sitter can be introduced later if declaration coverage or nesting
accuracy becomes a measured retrieval problem.

## Decision

Preserve chunk provenance through every retrieval and generation stage.

## Reason

Answers must cite actual repository files and line ranges. Chunk IDs,
relative paths, symbols, line ranges, and content remain available to BM25,
Qdrant, hybrid retrieval, reranking, and `/api/ask`.

## Alternatives

- Return only generated text
- Reconstruct citations from LLM output
- Store vectors without source metadata

## Tradeoff

Responses and vector payloads carry more metadata, but citations remain
verifiable and hallucinated locations are easier to detect.
