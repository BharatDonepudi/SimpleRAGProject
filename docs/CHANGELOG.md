# Changelog

Newest first. Each entry lists the commits (short SHA and subject), the modules touched, the test totals at that point, and the decisions taken. Dates are commit author dates. Architecture background is in [knowledge-base/README.md](knowledge-base/README.md); decisions are in [knowledge-base/decisions.md](knowledge-base/decisions.md).

The current totals were re-run on 2026-10-06 at `be3570f` and match: rag-service 25, backend 48, frontend 19. Per-commit counts for earlier commits (`d065033` 22, `1131c7d` 44, `3a6e764` 17, `864d09c` 23) are static counts of test methods at that commit, not separate runs.

---

## Phase 2 done and docs caught up (2026-10-06)

The commit after `be3570f`. The main session marked Phase 2 done after the browser check passed: the status row in `docs/implementation-plan.md` (plus a "Browser check passed" result line), the "Status" section of the root `README.md`, and "Current state" in [knowledge-base/README.md](knowledge-base/README.md). It also brings this changelog and the knowledge base up to date through `be3570f` (ADR-020 to ADR-022, run-book gaps 15–19), and aligns timing figures and the Phase 2 test count (run-book gaps 17 and 18). No code change.

---

## Phase 2: integration (2026-10-06): `2063d33`, `be3570f`

Commits, oldest first:

- `2063d33` rag-service: turn off gemma4 reasoning; return 500 on a blank answer. `RAGConfig.reasoning` (default `False`) is passed to `ChatOllama`. `/ask` returns `500` `{"detail": "answer generation failed"}` when the chain returns a blank string. Found in the Phase 2 browser check: the backend had returned 200 with `"answer": ""`, shown as an empty bubble. New tests `test_empty_answer_returns_500` and `test_build_chain_turns_off_model_reasoning_by_default`. Also updates `rag-service/README.md`. Tests: 25.
- `be3570f` Phase 2: add full-stack smoke test and record integration results. Adds `scripts/smoke.sh` (six checks against a running stack, exits non-zero on the first failure). Updates `README.md`, `CLAUDE.md`, `docs/implementation-plan.md` (Phase 2 results) and the knowledge base (run-book gaps 8, 13 and 14 closed).

Results recorded in [implementation-plan.md section 10](implementation-plan.md#10-phase-2--integration-integration-tester), as reported by the main session (live runs, not repeated for this entry):

- Unit suites: rag-service 23 at the time of the run (25 after `2063d33`), backend 48, frontend 19.
- Stack start in order: rag-service `ok` with 133 chunks in about 11 seconds, backend `UP` in about 5 seconds, then the frontend.
- `scripts/smoke.sh` passed. It also exits non-zero when rag-service or the backend is unreachable.
- Manual 503 check: with rag-service stopped, `POST /api/chat` returned 503 with `conversationId` and the user message was kept. After a restart, a retry in the same conversation returned 200.
- Browser check passed after `2063d33`: six questions from the page, all answered. Answers took 8–22 seconds, down from 40–84 seconds with reasoning on.

Not commits, but part of this round:

- **Shared venv fix.** `fastapi 0.142.2` needs `opentelemetry-api>=1.44` and had raised it to 1.45.0, while `opentelemetry-sdk` stayed at 1.41.0 (which pins the API exactly). `import chromadb` then failed with `_ExtendedAttributes`. The main session upgraded the OpenTelemetry family to 1.45.0 with pip, with the user's approval. `be52f79` pins the same versions.
- **Worktree cleanup.** All six worktrees under `.claude/worktrees/` were removed, and their branches deleted. See [Not on this branch](#not-on-this-branch) for the SHAs.

**Modules:** rag-service, scripts, root docs, knowledge base.
**Test totals at this point:** rag-service 25, backend 48, frontend 19 (re-run at `be3570f`).
**Decisions:**
- Turn off model reasoning and treat a blank answer as a failure. See [ADR-022](knowledge-base/decisions.md#adr-022-model-reasoning-off-and-a-blank-answer-is-a-500).
- The full path through a real model is checked by `scripts/smoke.sh`, not by unit tests. See [ADR-018](knowledge-base/decisions.md#adr-018-tests-never-need-ollama-the-network-or-other-modules).

---

## Post-Phase-1 fixes (2026-10-06): `864d09c`, `be52f79`, `a802c20`

Fixes for the two Phase 2 blockers (rag-service unreachable during startup, `chromadb` import failure) and the unpinned requirements.

- `864d09c` rag-service: build the index in a background thread so startup doesn't block. The lifespan used to `await` the build, and uvicorn binds the port only after startup returns, so `:8000` refused connections for the whole build and `/health` never showed `"loading"`. The lifespan now starts a daemon thread (`app.state.index_thread`) and returns at once. Tests use `started_client()`, which joins that thread; the new `test_startup_does_not_wait_for_index_build` holds the build open and checks `loading` and 503. Tests: 23.
- `be52f79` rag-service: pin requirements and drop unused packages. Every line in `requirements.txt` is pinned with `==`. Removed: `pdfplumber` (listed twice), `unstructured`, `unstructured[all-docs]`, `fastembed`, `sentence-transformers`, `elevenlabs`, `langchain`, `requests`. Added explicit pins for the OpenTelemetry family at 1.45.0. The commit message says it was verified with a fresh venv (pip check clean, 23 tests, live index build `ok` with 133 chunks).
- `a802c20` Docs: record the chromadb, startup and requirements fixes. `CLAUDE.md`, `docs/implementation-plan.md`, and the knowledge base (run-book gaps 1–3 and 12 fixed, rag-service module page, glossary).

**Modules:** rag-service, docs.
**Test totals at this point:** rag-service 23, backend 48, frontend 19.
**Decisions:**
- Build the index in a background thread. See [ADR-020](knowledge-base/decisions.md#adr-020-build-the-index-in-a-background-thread).
- Pin direct dependencies and keep the OpenTelemetry family on one version. See [ADR-021](knowledge-base/decisions.md#adr-021-pin-rag-service-dependencies-and-the-opentelemetry-family).

---

## Knowledge base and doc refresh (2026-10-06): `d38b5dc`

- `d38b5dc` Docs: add knowledge base and docs-curator agent; refresh stale docs after Phase 1. Adds `docs/knowledge-base/` and this changelog, and `.claude/agents/docs-curator.md` (listed in `docs/agents.md`). Refreshes `CLAUDE.md` (modules marked done, backend and frontend commands, start order), `docs/implementation-plan.md` (Phase 1A–1C done, Phase 2 blockers), `backend/README.md` (500 carries `conversationId`) and the root `README.md` (monorepo overview). No code change.

**Modules:** docs, `.claude/agents/`.
**Test totals at this point:** rag-service 22, backend 48, frontend 19.
**Decisions:** none new. The knowledge base records the existing ones as ADR-001 to ADR-019.

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

- `origin/main` is at `7627b43` (merge of PR #1, Claude GitHub Actions workflows, with `0f4926f` and `9c15818`). `feature/chat-rag-app` does not contain it. The local branch `worktree-agent-a217827573dcf24fc`, which pointed at it, was deleted; the commit is still on `origin/main`.
- Two superseded attempts at Phase 1B and 1C: `51edb5f` (backend, based on `7627b43`) and `ae0dff0` (frontend). They were replaced by `1131c7d` and `3a6e764`, and their branches were deleted.

### Branches deleted on 2026-10-06

All six worktrees under `.claude/worktrees/` were removed (all were clean), and these local branches were deleted. Each commit can be restored with `git branch <name> <sha>` until git garbage-collects it. Every SHA below was checked with `git cat-file -t`.

| Branch | Tip | State |
|---|---|---|
| `feature/rag-service-phase1a` | `d065033` | Merged in `58c163e` |
| `worktree-agent-acdd7b1c3a15d0f2f` | `2413a12` | Merged in `25c7cab` |
| `worktree-agent-a3eae953b85044496` | `3a6e764` | Merged (via `121f813`) in `7714c62` |
| `worktree-agent-ab3d57a8e89faf082` | `121f813` | Merged in `7714c62` |
| `worktree-agent-a2ae24f52a942091d` | `51edb5f` | Superseded backend attempt, not merged |
| `worktree-agent-a2dbec6326538da15` | `ae0dff0` | Superseded frontend attempt, not merged |
| `worktree-agent-a217827573dcf24fc` | `7627b43` | Still on `origin/main` |

The branch-to-SHA mapping comes from the main session's cleanup notes; git no longer records the branch names. The empty `.claude/worktrees/` folder was removed afterwards.
