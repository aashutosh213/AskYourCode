# AskYourCode

AskYourCode is a local, free-only codebase search and understanding application for developers.

## Current status

This repository is in Phase 0: project foundation setup.

## Stack

- Next.js + TypeScript + Tailwind
- Java 21 + Spring Boot + Maven
- PostgreSQL local
- Qdrant OSS local
- Ollama local

## Local requirements

- Java 21
- Maven
- Node.js 20+
- Docker Engine or Podman

## Run

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Notes

The application intentionally does not perform retrieval or generation yet. The current goal is to establish the foundation and verify the local development environment.
