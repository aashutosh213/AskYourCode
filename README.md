# AskYourCode

Local-first semantic search and question answering for source-code repositories.

AskYourCode indexes a codebase, retrieves relevant symbols with exact and semantic search, reranks candidates, and asks a locally running language model to explain the code with file and line-range citations.

> Software and API cost: **$0**. Source code and prompts stay on the local machine.

## Current capabilities

- Scans local repositories while ignoring generated and dependency directories.
- Parses Java, JavaScript, TypeScript, and Python declarations into semantic chunks.
- Generates local embeddings through Ollama with a deterministic fallback.
- Searches vectors with Qdrant OSS and exact identifiers with Apache Lucene BM25.
- Combines vector and keyword results with reciprocal-rank fusion.
- Applies explainable local reranking.
- Generates grounded answers through a local Ollama chat model.
- Returns numbered citations with source paths, symbols, and line ranges.
- Provides Recall@K and reciprocal-rank retrieval evaluation.

The backend APIs are implemented. The Next.js frontend is currently a shell; repository, search, ask, and source-viewer workflows are next.

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

The backend is a Spring Boot modular monolith. H2 is currently used for self-contained development and tests; PostgreSQL is the target local metadata store. Qdrant stores vectors and Lucene provides BM25 search.

## Technology

- Java 21, Spring Boot, Maven
- Next.js, React, TypeScript, Tailwind CSS
- H2 for current development metadata persistence
- PostgreSQL for the planned normal local metadata store
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
docker exec -it askyourcode-ollama ollama pull qwen2.5-coder:7b
```

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

The backend runs on `http://localhost:8080`.

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

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Health check |
| `POST /api/repositories/index` | Index a local repository |
| `GET /api/chunks` | Browse indexed chunks |
| `POST /api/search/keyword` | BM25 keyword search |
| `POST /api/search/vector` | Semantic vector search |
| `POST /api/search/hybrid` | BM25 + vector RRF search |
| `POST /api/search/reranked` | Hybrid search with local reranking |
| `POST /api/ask` | Local LLM answer with citations |

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

- Complete the frontend repository, search, ask, and source-viewer workflows.
- Move normal metadata persistence from H2 to local PostgreSQL migrations.
- Improve indexing stages, failure reporting, and re-indexing cleanup.
- Add file hashes and incremental indexing.
- Expand retrieval evaluation and citation-grounding tests.
- Add an optional local cross-encoder reranker.

## Project documentation

- [`AI_DEVELOPMENT_PROMPT.md`](.ai/AI_DEVELOPMENT_PROMPT.md ) — master development requirements.
- [`PROJECT_STATE.md`](.ai/PROJECT_STATE.md) — current verified state.
- [`TODO.md`](.ai/TODO.md) — active backlog and blocked verification.
- [`ARCHITECTURE.md`](.ai/ARCHITECTURE.md) — current architecture.
- [`DECISIONS.md`](.ai/DECISIONS.md) — architectural decisions and tradeoffs.
- [`CHANGELOG.md`](.ai/CHANGELOG.md) — meaningful project changes.

## License

See [LICENSE](LICENSE).
