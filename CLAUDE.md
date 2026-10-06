# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Monorepo for a browser chat that answers questions about a PDF using a local LLM through Ollama. No hosted LLM APIs are involved.

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    └── H2 file DB (transcript log)
```

| Folder | What | Status |
|---|---|---|
| `rag-service/` | FastAPI (`app.py`) around the LangChain RAG pipeline (`pdf_rag.py`, `prompts.py`) over `rag-service/data/HOA.pdf` | done (Phase 1A) |
| `backend/` | Spring Boot 4.1 (Maven, Java 21) chat REST API with an H2 transcript log | done (Phase 1B) |
| `frontend/` | React + Vite chat page, plain JavaScript | done (Phase 1C) |
| `scripts/` | `smoke.sh` full-stack check | pending (Phase 2) |
| `docs/api-contract.md` | **Source of truth** for every HTTP boundary. Modules must match it; don't change it from inside a module | done |
| `docs/implementation-plan.md` | Phased plan and status; update its Status table as phases finish | done |
| `docs/agents.md` | How the parallel subagents are set up and launched | done |
| `docs/knowledge-base/`, `docs/CHANGELOG.md` | Onboarding docs, decisions (ADRs), run book, per-merge changelog | done |

`starter-1.py` and `start-2.py` at the root are standalone Ollama demos (raw HTTP and the `ollama` client), unrelated to the app.

## Working rules

- Each module is owned by one subagent (`.claude/agents/`): `rag-service-dev`, `backend-dev`, `frontend-dev`, plus `integration-tester` for scripts and root docs and `docs-curator` for `docs/knowledge-base/` and `docs/CHANGELOG.md`. When acting as one of them, edit only that module's folder.
- Contract changes go through the main session: edit `docs/api-contract.md`, commit, then update the affected modules.
- The Python virtualenv is `.venv/` at the repo root and is shared. It is gitignored, so it doesn't exist inside git worktrees; use the absolute path `/Users/keerthikambhampati/gitRepo/LLMUsingApi/.venv/bin/python`.
- Unit tests in every module must run without Ollama, the network or the other modules.

## Commands

```bash
# rag-service (run from rag-service/; the doc path ./data/HOA.pdf is relative to it)
cd rag-service
../.venv/bin/uvicorn app:app --port 8000                          # the service
../.venv/bin/python pdf_rag.py                                    # CLI: one hardcoded question
../.venv/bin/python -m unittest discover -s tests -v              # all tests
../.venv/bin/python -m unittest tests.test_pdf_rag.PDFRAGTests.test_run_preserves_pipeline_flow

# Ollama must be running with these models
ollama pull gemma4
ollama pull nomic-embed-text
```

```bash
# backend (needs rag-service on :8000 to answer; otherwise POST /api/chat returns 503)
cd backend
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=ChatServiceTest

# frontend (needs the backend on :8080; Vite proxies /api to it)
cd frontend
npm install
npm run dev                   # http://localhost:5173
npm test -- --run
npx vitest run src/App.test.jsx
npm run build
```

Start order for the full stack: Ollama → rag-service (wait for `GET :8000/health` to report `ok`) → backend → frontend. Each module README has the details.

## rag-service architecture

- `RAGConfig` (`pdf_rag.py`) is a frozen dataclass holding all tunables: PDF path, chat model `gemma4`, embedding model `nomic-embed-text`, Chroma collection, chunk size/overlap, the CLI question. The first three can be overridden with `RAG_DOC_PATH`, `RAG_MODEL` and `RAG_EMBED_MODEL`, read when the config is constructed. Change behavior through config, not by editing methods.
- `PDFRAGApp.build_index()` runs `load_documents` → `split_documents` → `build_vector_db` → `build_chain` and stores `self.chain` and `self.chunk_count`. `answer(question)` invokes the chain and raises `RuntimeError` if the index isn't built. `run()` is the CLI wrapper (`build_index()` then `answer(config.question)`). Each step is its own method so tests can patch it.
- `load_documents` raises `FileNotFoundError` when the PDF is missing.
- `build_vector_db` calls `ollama.pull()` on the embedding model, then builds an in-memory Chroma store from scratch (nothing is persisted). This is slow, which is why the service builds it once at startup.
- `build_chain` wraps the vector store in a `MultiQueryRetriever` (the LLM rewrites the question into five variants), then pipes `{context, question}` → `rag_answer_prompt()` → `ChatOllama` → `StrOutputParser`.
- `prompts.py` holds every prompt as a named constant plus a builder (`query_rewrite_prompt()`, `rag_answer_prompt()`). The answer prompt includes a guardrail: say so when the context doesn't contain the answer. A new prompt needs a constant here and a test of its `input_variables`.
- `app.py`: the FastAPI lifespan builds the index once and sets `app.state.index` (an `IndexState`) to `ok` or `error` with a `detail`. `/ask` is a sync `def`, so the chain call runs in the threadpool. It returns 503 while the index is loading or failed, and 500 if the chain raises. `/health` returns `status`, `chunks` and, on error, `detail`.
- Known gap: the lifespan awaits the build, so uvicorn doesn't bind `:8000` until it finishes, and `/health` never actually shows `loading` to a caller. The module docstring says the build runs in the background, which isn't accurate yet.
- Tests import `pdf_rag` normally (the test file puts `rag-service/` on `sys.path`) and patch the `PDFRAGApp` step methods; `test_app.py` uses FastAPI's `TestClient`.

## backend architecture

Package `com.llmusingapi.backend`: `chat/` (controller, `ChatService`, DTO records), `rag/` (`RagClient` on `RestClient`, config properties `rag.service.url` and `rag.service.read-timeout`), `persistence/` (`Conversation`, `Message`, repositories), `web/` (`GlobalExceptionHandler`). `ChatService` saves the user message before calling rag-service; any failure after the conversation exists becomes `AnswerUnavailableException`, which maps to 503, 504 or 500 with `conversationId` in the body.
