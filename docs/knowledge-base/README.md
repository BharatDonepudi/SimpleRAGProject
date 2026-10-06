# Knowledge base

Start here if you are new to this repo. It explains what the app does, how the parts fit together, how to run them, why they are built this way, and what changed in each phase. It was checked against the code and git history on `feature/chat-rag-app` through commit `7714c62` (2026-10-06).

The app is a browser chat that answers questions about one PDF (`rag-service/data/HOA.pdf`) with a local LLM through Ollama. No hosted LLM API is used.

## Reading order

1. [architecture.md](architecture.md): the four processes, the ports, how one question flows through them, and what is stored where.
2. [run-book.md](run-book.md): prerequisites, start order, health checks, the tests to run, common failures, and known gaps.
3. Module pages, in any order:
   - [modules/rag-service.md](modules/rag-service.md): Python FastAPI service, the RAG pipeline and its prompts.
   - [modules/backend.md](modules/backend.md): Spring Boot API, transcript log in H2, error mapping.
   - [modules/frontend.md](modules/frontend.md): React chat page and its API client.
4. [decisions.md](decisions.md): why things are built this way (ADRs), with the commit that landed each one.
5. [glossary.md](glossary.md): terms used in the code and docs.
6. [contract-history.md](contract-history.md): every change to the HTTP contract, with its commit.

Reference documents outside this folder:

| Document | What it is for |
|---|---|
| [docs/api-contract.md](../api-contract.md) | Source of truth for every HTTP boundary. Read it before changing any API. |
| [docs/CHANGELOG.md](../CHANGELOG.md) | What landed in each phase or merge, with commit SHAs and test totals. |
| [docs/implementation-plan.md](../implementation-plan.md) | The phased plan, decisions table and status. Its status table is out of date; see the run-book. |
| [docs/agents.md](../agents.md) | How the parallel subagents were set up and launched. |
| [CLAUDE.md](../../CLAUDE.md) | Working rules for agents and the main session. |

## Repo at a glance

| Folder | What |
|---|---|
| `rag-service/` | Python. `pdf_rag.py` (pipeline), `prompts.py`, `app.py` (FastAPI), `tests/`. |
| `backend/` | Java 21 and Spring Boot. Chat REST API, H2 transcript log, rag-service client. |
| `frontend/` | React 19 and Vite, plain JavaScript. Chat page. |
| `docs/` | Contract, plan, agent guide, changelog and this knowledge base. |
| `.claude/` | Agent definitions and the agent command allowlist. |
| `.venv/` | Shared Python virtualenv at the root (gitignored). |

`starter-1.py` and `start-2.py` at the root are standalone Ollama demos. They are not part of the app.

## Current state

- Phase 0 (foundation) and Phase 1 (rag-service, backend, frontend) are merged on `feature/chat-rag-app`.
- Phase 2 (integration) is under way: the full stack runs and `scripts/smoke.sh` passes (2026-10-06). The browser check is still to do.
- Tests run without Ollama or the network: rag-service 22, backend 48, frontend 19. All pass at `7714c62`.
- Known gaps are listed in [run-book.md](run-book.md#known-gaps). The most important one: rag-service does not answer HTTP while its index is building, so `/health` cannot show `"loading"` at startup.

## Conventions

- Every HTTP boundary is defined in `docs/api-contract.md`. Changes go through the main session, not a module.
- Each module has one owner. Owners edit only their own folder.
- Tests never need Ollama, the network or another module.
- Commit SHAs in these docs are short SHAs from `git log`. They can be checked with `git cat-file -t <sha>`.
