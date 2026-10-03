# AskYourCode

[![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black?logo=next.js)](https://nextjs.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Local-first semantic search and question answering for source-code repositories.

AskYourCode indexes a codebase, retrieves relevant symbols with exact and semantic search, reranks candidates, and asks a locally running language model to explain the code with file and line-range citations.

> Software and API cost: **$0**. Source code and prompts stay on the local machine.

## Contents

- [Current capabilities](#current-capabilities)
- [Architecture](#architecture)
- [Run locally](#run-locally)
- [API examples](#api-examples)
- [Tests](#tests)
- [Roadmap](#roadmap)

## Current capabilities

- Scans local repositories while ignoring generated and dependency directories.
- Parses Java, JavaScript, TypeScript, and Python declarations into semantic chunks.
- Generates local embeddings through Ollama with a deterministic fallback.
- Searches vectors with Qdrant OSS and exact identifiers with Apache Lucene BM25.
- Combines vector and keyword results with reciprocal-rank fusion.
- Applies explainable local reranking.
- Generates grounded answers through a local Ollama chat model.
- Returns numbered citations with source paths, symbols, and line ranges.
- Opens cited source files in the frontend with highlighted line ranges.
- Provides Recall@K and reciprocal-rank retrieval evaluation.

The backend APIs and Next.js repository, search, ask, and source-viewer
workflows are implemented.

## Architecture

```text
Local repository → Scan → Parse → Semantic chunks → Local embeddings → Qdrant
                                                                    │
Query → BM25 keyword search ────────────────────────────────────────┤
Query → Qdrant vector search ──────────────────────────────────────┘
                              ↓
                    RRF hybrid fusion → Local reranker
                              ↓
                    Context with provenance → Ollama
                              ↓
                         Answer + citations
```

The backend is a Spring Boot modular monolith. PostgreSQL is the normal local
metadata store and Flyway owns its schema migrations; H2 remains available for
self-contained tests. Qdrant stores vectors and Lucene provides BM25 search.

## Technology

- Java 21, Spring Boot, Maven
- Next.js, React, TypeScript, Tailwind CSS
- PostgreSQL with Flyway for normal local metadata persistence
- H2 for self-contained tests
- Qdrant OSS for vector search
- Apache Lucene for BM25 search
- Ollama for local embeddings and LLM generation
- JUnit 5, Spring Boot Test, and Mockito

## Requirements

- Java 21
- Maven
- Node.js 20+
- Docker Engine with Compose, or Podman with Compose
- Local CPU/RAM/VRAM suitable for the selected Ollama models

No paid API key or cloud account is required.

## Run locally

Start local services:

```bash
cd infrastructure
docker compose up -d
```

Pull the local models:

```bash
docker exec -it askyourcode-ollama ollama pull nomic-embed-text
docker exec -it askyourcode-ollama ollama pull qwen2.5-coder:1.5b
```

The backend default is `qwen2.5-coder:1.5b`, configured in
`backend/src/main/resources/application.properties`. You can select another
local model by changing `ollama.chat.model` before starting the backend.

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

The backend runs on `http://localhost:8080`.

The backend expects the local PostgreSQL service from Compose at
`localhost:5432` by default. Flyway creates the metadata schema on startup.
The `SPRING_DATASOURCE_*` variables can override the local connection without
introducing any cloud dependency.

Start the frontend when npm registry access is available:

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:3000`.

## API examples

```bash
curl http://localhost:8080/api/health
```

Index a local repository:

```bash
curl -X POST http://localhost:8080/api/repositories/index \
  -H 'Content-Type: application/json' \
  -d '{"repositoryPath":"/absolute/path/to/repository"}'
```

Hybrid search:

```bash
curl -X POST http://localhost:8080/api/search/hybrid \
  -H 'Content-Type: application/json' \
  -d '{"query":"Where is JWT validation implemented?","repositoryPath":"/absolute/path/to/repository","limit":5}'
```

Ask a grounded question:

```bash
curl -X POST http://localhost:8080/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"How does authentication work?","repositoryPath":"/absolute/path/to/repository","limit":5}'
```

The ask endpoint returns a job ID immediately. Poll `GET /api/ask/jobs/{jobId}` until the status is `COMPLETED` or `FAILED`.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Health check |
| `POST /api/repositories/index` | Index a local repository |
| `GET /api/repositories/index/{jobId}` | Read repository indexing job status |
| `GET /api/chunks` | Browse indexed chunks |
| `POST /api/search/keyword` | BM25 keyword search |
| `POST /api/search/vector` | Semantic vector search |
| `POST /api/search/hybrid` | BM25 + vector RRF search |
| `POST /api/search/reranked` | Hybrid search with local reranking |
| `GET /api/search/jobs/{jobId}` | Read reranked search job status |
| `POST /api/ask` | Queue local LLM answer generation |
| `GET /api/ask/jobs/{jobId}` | Read answer job status and result |
| `GET /api/source` | Read a cited source range from an indexed repository |

Send `"force": true` to `POST /api/repositories/index` after changing repository files. This clears the existing parsed chunks, embeddings, and Qdrant collection before rebuilding the index.

## Tests

```bash
cd backend
mvn test
```

Focused parser test:

```bash
mvn -q -Dtest=CodeParserServiceTest test
```

The live Qdrant integration test requires a reachable local Qdrant service. A restricted sandbox may deny socket creation, so that test can fail there even when the application code is otherwise healthy.

## Roadmap

- Improve indexing stages, failure reporting, and re-indexing cleanup.
- Improve indexing stages, failure reporting, and re-indexing cleanup.
- Add file hashes and incremental indexing.
- Expand retrieval evaluation and citation-grounding tests.
- Add an optional local cross-encoder reranker.

## Project documentation

- `AI_DEVELOPMENT_PROMPT.md` — master development requirements. The filename
  currently contains a trailing space in the repository.
- [`PROJECT_STATE.md`](.ai/PROJECT_STATE.md) — current verified state.
- [`TODO.md`](.ai/TODO.md) — active backlog and blocked verification.
- [`ARCHITECTURE.md`](.ai/ARCHITECTURE.md) — current architecture.
- [`DECISIONS.md`](.ai/DECISIONS.md) — architectural decisions and tradeoffs.
- [`CHANGELOG.md`](.ai/CHANGELOG.md) — meaningful project changes.

## License

See [LICENSE](LICENSE).
