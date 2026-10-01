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
