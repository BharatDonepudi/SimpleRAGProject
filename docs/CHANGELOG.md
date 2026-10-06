# Changelog

Newest first. Each entry lists the commits (short SHA and subject), the modules touched, the test totals at that point, and the decisions taken. Dates are commit author dates. Architecture background is in [knowledge-base/README.md](knowledge-base/README.md); decisions are in [knowledge-base/decisions.md](knowledge-base/decisions.md).

The current totals were re-run on 2026-10-06 and match: rag-service 22, backend 48, frontend 19. The per-commit counts for `1131c7d` (44), `3a6e764` (17) and `d065033` (22) are static counts of test annotations at that commit, not separate runs.

---

## Frontend merge (2026-10-06): `7714c62`

Merge of `worktree-agent-ab3d57a8e89faf082` into `feature/chat-rag-app`. Brings in:

- `3a6e764` Phase 1C: React chat frontend (Vite, Vitest + RTL). Adds `frontend/`: `App.jsx`, `ChatWindow.jsx`, `MessageInput.jsx`, `api/chatClient.js`, `storage.js`, `vite.config.js` (proxies `/api` to `:8080`), tests. Built on `06a65c5`. Tests: 17 at this commit.
- `121f813` Frontend: keep `conversationId` on 500 as well as 503/504. `App.jsx` keeps the stored id on 500, 503 and 504 when the body carries one. Follows `ce192d9`. Tests: 19.

**Modules:** frontend.
**Test totals at this point:** rag-service 22, backend 48, frontend 19.
**Decisions:**
- The conversation id lives in `sessionStorage`, so each browser tab has its own conversation. See [ADR-016](knowledge-base/decisions.md#adr-016-conversation-id-in-sessionstorage).
- The page keeps the id on 500/503/504 because the backend has already saved the user message. See [ADR-011](knowledge-base/decisions.md#adr-011-conversationid-on-503-504-and-500).
- No token streaming; the page shows a "Thinking" state until the full answer arrives. See [ADR-004](knowledge-base/decisions.md#adr-004-full-answer-and-a-spinner-no-streaming).

---

## Backend merge (2026-10-06): `25c7cab`

Merge of `worktree-agent-acdd7b1c3a15d0f2f` into `feature/chat-rag-app`. Brings in:

- `1131c7d` Phase 1B: Spring Boot backend for chat API. Adds `backend/`: Spring Boot 4.1.1 (Maven wrapper, Java 21), `ChatController`, `ChatService`, `RagClient` (`RestClient`), `GlobalExceptionHandler`, JPA entities `Conversation` and `Message` in H2 (`backend/data/chatdb`), config keys `rag.service.url` and `rag.service.read-timeout`. Built on `06a65c5`. Tests: 44 at this commit.
- `2413a12` Backend: include `conversationId` on 500 once the conversation exists. `ChatService` wraps any failure after the conversation exists in `AnswerUnavailableException`, so 500 bodies carry the id. Tests: 48.

**Modules:** backend.
**Test totals at this point:** rag-service 22, backend 48 (frontend not yet on this branch).
**Decisions:**
- Save the user message before calling rag-service, with no transaction spanning the call. See [ADR-010](knowledge-base/decisions.md#adr-010-save-the-user-message-before-the-rag-call).
- `conversationId` on 503, 504 and 500. See [ADR-011](knowledge-base/decisions.md#adr-011-conversationid-on-503-504-and-500).
- H2 file database for the transcript log instead of Excel, SQLite or JSON. See [ADR-002](knowledge-base/decisions.md#adr-002-h2-file-database-for-the-transcript-log).

---

## rag-service merge (2026-10-06): `58c163e`

Merge of `feature/rag-service-phase1a` into `feature/chat-rag-app`. Brings in:

- `d065033` Phase 1A: wrap rag-service in FastAPI with prompts module and tests. Adds `rag-service/app.py` (FastAPI lifespan builds the index once; `POST /ask`, `GET /health`), `rag-service/prompts.py` (named prompt constants plus builders, with a guardrail sentence), `RAGConfig` env overrides (`RAG_DOC_PATH`, `RAG_MODEL`, `RAG_EMBED_MODEL`), and `PDFRAGApp.build_index()` and `answer()`. `run()` and `main()` stay as the CLI. Built on `acc6607`. Tests: 22.

**Modules:** rag-service.
**Test totals at this point:** rag-service 22 (backend and frontend not yet on this branch).
**Decisions:**
- A long-running FastAPI service instead of a subprocess per question. See [ADR-001](knowledge-base/decisions.md#adr-001-long-running-fastapi-service-for-the-rag-code).
- Prompts are version-controlled constants, not YAML or Jinja. See [ADR-014](knowledge-base/decisions.md#adr-014-prompts-as-constants-with-builder-functions).
- Known issue at merge: the index is built inside the lifespan, which uvicorn runs before it binds the port. See [run-book known gaps](knowledge-base/run-book.md#known-gaps).

---

## Contract change: `ce192d9` (2026-10-06)

`docs/api-contract.md`: Contract A now also returns `conversationId` on `500`, and the `500` row covers rag-service errors (its 500 or 422) and anything unexpected. The user message is saved before rag-service is called, so the client should keep the id on 500 too.

**Modules affected:** backend (`2413a12`), frontend (`121f813`). rag-service unchanged.
**Decision:** see [ADR-011](knowledge-base/decisions.md#adr-011-conversationid-on-503-504-and-500). Full history in [contract-history.md](knowledge-base/contract-history.md).

---

## Contract change: `06a65c5` (2026-10-06)

`docs/api-contract.md`: `503` and `504` bodies now carry `conversationId` once the conversation exists. The `400` row also covers a malformed `conversationId`, and `405` and `415` were added to the status table. Those two statuses were already returned by the backend.

**Modules affected:** backend (`1131c7d`), frontend (`3a6e764`). rag-service unchanged.
**Decision:** see [ADR-011](knowledge-base/decisions.md#adr-011-conversationid-on-503-504-and-500).

---

## Phase 0: foundation (2026-10-05)

- `b8b0294` Phase 0: restructure into monorepo, add API contract and agents. Moves `pdf-rag.py` to `rag-service/pdf_rag.py`, `data/HOA.pdf` to `rag-service/data/`, `requirements.txt` to `rag-service/` (adds `fastapi`, `uvicorn`, `httpx`), and `tests/` to `rag-service/tests/`. Adds `docs/api-contract.md`, `docs/agents.md`, `docs/agent-notes.md`, `CLAUDE.md`, `.claude/agents/` (four agent definitions), `.claude/settings.json`, `.gitignore`. Rewrites `README.md`. Tests: 2 (`rag-service/tests/test_pdf_rag.py`).
- `acc6607` Add implementation plan doc; drop references to removed `/agents` wizard. Adds `docs/implementation-plan.md` and trims references in `CLAUDE.md`, `docs/agents.md` and `docs/agent-notes.md`. No code change.

**Modules touched:** repo-wide layout, rag-service (moved), docs, `.claude/`.
**Decisions:** monorepo, contract-first development, and one subagent per module in its own worktree. See [ADR-005](knowledge-base/decisions.md#adr-005-monorepo), [ADR-008](knowledge-base/decisions.md#adr-008-contract-first-one-owner-per-module) and [ADR-009](knowledge-base/decisions.md#adr-009-parallel-subagents-in-git-worktrees).

---

## Before Phase 0: original PDF RAG script

- `615a2d8` adding readme (2026-04-11). Adds the original `README.md`.
- `7c26c7d` RAG Implementation in Python using LangChain (2026-04-10). Adds `pdf-rag.py` (one hardcoded question, Chroma built on every run), `requirements.txt` and `data/HOA.pdf`. This is the code that Phase 1A refactored.

---

## Not on this branch

- `origin/main` has `7627b43` (merge of PR #1, Claude GitHub Actions workflows, with `0f4926f` and `9c15818`). `feature/chat-rag-app` does not contain it.
- Two superseded attempts at Phase 1B and 1C exist on worktree branches, not on this branch: `51edb5f` (backend, based on `7627b43`) and `ae0dff0` (frontend). They were replaced by `1131c7d` and `3a6e764`.
