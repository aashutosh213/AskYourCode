# TODO

## Current Sprint

- [x] Create the project root structure and metadata files
- [x] Scaffold the Spring Boot backend and health endpoint
- [x] Validate the backend health response
- [x] Initialize the Next.js frontend shell
- [x] Add local infrastructure compose configuration
- [x] Correct the Java 21 project configuration and build environment
- [x] Harden repository ignore scanning for nested generated directories
- [x] Verify the ingestion and health tests under the actual local JDK
- [x] Expand repository indexing into a file-metadata persistence model
- [x] Add file metadata persistence for repository ingestion
- [x] Create a repository index job state model
- [x] Add parsing and semantic chunking
- [x] Add pagination and file-filtering to chunks API
- [x] Add embeddings and Qdrant storage
- [x] Add Lucene BM25 keyword search with source metadata

## Next

- [x] Run the backend regression suite after Phase 2 changes
- [ ] Test end-to-end vector search pipeline with live Qdrant
- [ ] Build hybrid retrieval and reranking
- [x] Add reciprocal-rank-fusion hybrid retrieval
- [ ] Add local LLM generation and citations

## Upcoming

- [ ] Add retrieval evaluation and reranking
- [ ] Add local LLM generation and citations
- [ ] Expand parsing to TypeScript/JavaScript/Python
- [x] Add BM25 keyword search

## Later

- [ ] Add frontend implementation (blocked by npm registry)
- [ ] Add user interface for search and ask
- [ ] Add search history tracking

## Completed

- [x] Confirmed Java 21, Maven, and Node.js are installed locally
- [x] Identified the project as a clean Phase 0 restart
- [x] Implemented Phase 0 backend foundation and health endpoint
- [x] Implemented the first ingestion controller and scanner skeleton
- [x] Verified the Spring Boot tests for health and repository ingestion
- [x] Fixed the Maven/Java mismatch and Mockito attach configuration for this environment
- [x] Implemented Java parsing and semantic chunking with javaparser
- [x] Implemented chunk persistence and retrieval API
- [x] Added Qdrant Java client dependency
- [x] Created QdrantConfig for client bean configuration
- [x] Rewrote QdrantEmbeddingClient with official Qdrant client
- [x] Enhanced LocalEmbeddingService with progress logging
- [x] Created VectorSearchService and DTOs
- [x] Created VectorSearchController API endpoint
- [x] Added VectorSearchIntegrationTest
- [x] Increased embedding JSON persistence capacity for 768-dimensional vectors
