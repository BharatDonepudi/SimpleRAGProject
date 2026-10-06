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
| `rag-service/` | Python LangChain RAG over `rag-service/data/HOA.pdf` (`pdf_rag.py`), to be wrapped in FastAPI | exists; FastAPI wrapper pending |
| `backend/` | Spring Boot (Maven, Java 21) chat REST API | pending |
| `frontend/` | React + Vite chat page | pending |
| `docs/api-contract.md` | **Source of truth** for every HTTP boundary. Modules must match it; don't change it from inside a module | done |
| `docs/implementation-plan.md` | Phased plan and status; update its Status table as phases finish | done |
| `docs/agents.md` | How the parallel subagents are set up and launched | done |

`starter-1.py` and `start-2.py` at the root are standalone Ollama demos (raw HTTP and the `ollama` client), unrelated to the app.

## Working rules

- Each module is owned by one subagent (`.claude/agents/`): `rag-service-dev`, `backend-dev`, `frontend-dev`, plus `integration-tester` for scripts and root docs. When acting as one of them, edit only that module's folder.
- Contract changes go through the main session: edit `docs/api-contract.md`, commit, then update the affected modules.
- The Python virtualenv is `.venv/` at the repo root and is shared. It is gitignored, so it doesn't exist inside git worktrees; use the absolute path `/Users/keerthikambhampati/gitRepo/LLMUsingApi/.venv/bin/python`.
- Unit tests in every module must run without Ollama, the network or the other modules.

## Commands

```bash
# rag-service (run from rag-service/; the doc path ./data/HOA.pdf is relative to it)
cd rag-service
../.venv/bin/python pdf_rag.py                                    # CLI: one hardcoded question
../.venv/bin/python -m unittest discover -s tests -v              # all tests
../.venv/bin/python -m unittest tests.test_pdf_rag.PDFRAGTests.test_run_preserves_pipeline_flow

# Ollama must be running with these models
ollama pull gemma4
ollama pull nomic-embed-text
```

Backend and frontend commands will live in their module READMEs once those modules exist.

## rag-service/pdf_rag.py architecture

- `RAGConfig` is a frozen dataclass holding all tunables (PDF path, chat model `gemma4`, embedding model `nomic-embed-text`, Chroma collection, chunk size/overlap, the hardcoded question). Change behavior by passing a different `RAGConfig`, not by editing methods.
- `PDFRAGApp.run()` is a fixed pipeline: `load_documents` → `split_documents` → `build_vector_db` → `build_chain` → `chain.invoke(question)`. Each step is its own method so it can be patched in tests.
- `build_vector_db` calls `ollama.pull()` on the embedding model, then builds an in-memory Chroma store from scratch (nothing is persisted). This is slow, which is why the service builds it once at startup.
- `build_chain` wraps the vector store in a `MultiQueryRetriever` (the LLM rewrites the question into five variants), then pipes `{context, question}` → `ChatPromptTemplate` → `ChatOllama` → `StrOutputParser`.
- `tests/test_pdf_rag.py` currently loads the module by file path with `importlib`, and mocks the `PDFRAGApp` step methods.
