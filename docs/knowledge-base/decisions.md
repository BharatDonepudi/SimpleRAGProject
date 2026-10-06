# Decisions

Architecture decision records (ADRs). Each one gives the context, the decision, the alternatives that were rejected, the consequences, and where it landed (commit or phase). The first group comes from the decisions table in [implementation-plan.md](../implementation-plan.md#2-decisions); the rest come from the code and the commits. The status of every ADR is **Accepted** unless noted.

Decisions are not revisited here. To change one, write a new ADR that supersedes it.

---

## ADR-001: Long-running FastAPI service for the RAG code

- **Context:** The original `pdf-rag.py` ran as a script that rebuilt the whole Chroma index on every run. The backend is Java and needs to call the RAG code per question.
- **Decision:** Wrap the RAG pipeline in a FastAPI service that builds the index once at startup and answers `POST /ask`.
- **Rejected:** A subprocess per request. Slow (re-embeds the PDF each time) and fragile to error handling.
- **Consequences:** Startup is slow and the index lives in memory, so a restart rebuilds it. Index build and answering share one process. See [run-book known gaps](run-book.md#known-gaps) for the startup-blocking issue.
- **Landed:** `d065033` (merged in `58c163e`). Plan: [section 2](../implementation-plan.md#2-decisions).

## ADR-002: H2 file database for the transcript log

- **Context:** The backend needs to store conversations and messages. Writes can come from more than one request.
- **Decision:** H2 in file mode (`jdbc:h2:file:./data/chatdb`), with JPA and `ddl-auto=update`.
- **Rejected:** Excel (cannot handle two writes at once). SQLite (needs a community dialect with Spring). JSON files (not queryable).
- **Consequences:** Embedded, real SQL, no server to run. The H2 console is enabled in the one configuration file (`application.properties`); there are no Spring profiles. Moving to Postgres later means changing the datasource and dialect, not the code.
- **Landed:** `1131c7d` (merged in `25c7cab`).

## ADR-003: Transcript-only memory, RAG chain unchanged

- **Context:** Follow-up questions would benefit from history, but the chain was already working for one question at a time.
- **Decision:** Store the transcript for the user. Send only the current question to rag-service. History is not added to the RAG prompt.
- **Rejected:** Feeding history into the chain in v1.
- **Consequences:** Follow-up questions are answered without the earlier turns. This is listed as later work in the [plan](../implementation-plan.md#15-later-out-of-scope-for-v1). In code, `ChatService` passes only `request.message()` to `ragClient.ask`.
- **Landed:** `1131c7d`.

## ADR-004: Full answer and a spinner, no streaming

- **Context:** Local model answers take 10 to 60 seconds. Streaming would need SSE in all three layers.
- **Decision:** Return the full answer in one response. The frontend shows a "Thinking" bubble while it waits.
- **Rejected:** Token streaming (SSE in every layer).
- **Consequences:** Simple and robust across the three layers. The user waits with no partial text. Streaming is listed as later work.
- **Landed:** Plan decision; implemented in `1131c7d` (backend) and `3a6e764` (frontend).

## ADR-005: Monorepo

- **Context:** Three modules have to be built in parallel against one contract.
- **Decision:** One repository with `rag-service/`, `backend/`, `frontend/`, and `docs/`. Each module has one owner.
- **Rejected:** Separate repositories.
- **Consequences:** One clone and one contract file. Shared `.venv` at the root. Git worktrees are needed for parallel work (see [ADR-009](#adr-009-parallel-subagents-in-git-worktrees)).
- **Landed:** `b8b0294`.

## ADR-006: Maven wrapper for the backend build

- **Context:** The backend needs a build tool that a new engineer can run without setup.
- **Decision:** Maven, run through the committed `./mvnw` wrapper (Spring Boot 4.1.1 parent, Java 21).
- **Rejected:** Gradle.
- **Consequences:** No Maven install is needed. Commands are `./mvnw spring-boot:run` and `./mvnw test`.
- **Landed:** `1131c7d`. `backend/pom.xml`.

## ADR-007: Plain JavaScript for the frontend

- **Context:** The frontend is a small chat page.
- **Decision:** React 19 with Vite 8, in plain JavaScript (`.jsx` and `.js`), no TypeScript.
- **Rejected:** TypeScript.
- **Consequences:** Less setup. No type checking on the client; tests are Vitest and React Testing Library.
- **Landed:** `3a6e764`.

## ADR-008: Contract-first, one owner per module

- **Context:** The three modules were built by separate agents at the same time. Without a fixed contract they would disagree.
- **Decision:** `docs/api-contract.md` is the single source of truth for every HTTP boundary. It was written before any module code. No module changes it alone. Changes go through the main session: edit the contract, commit, then update the affected modules.
- **Rejected:** Letting each module define its own interface and reconciling later.
- **Consequences:** Two contract changes so far, both in the Phase 1 window: `06a65c5` ([history](contract-history.md)) and `ce192d9` ([history](contract-history.md)). Each one was followed by module commits that implemented it.
- **Landed:** `b8b0294` (contract). Rule in [CLAUDE.md](../../CLAUDE.md).

## ADR-009: Parallel subagents in git worktrees

- **Context:** Phases 1A to 1C run in parallel.
- **Decision:** Four subagents, each with its own folder and its own git worktree under `.claude/worktrees/`. Each branch is merged into `feature/chat-rag-app` after review.
- **Rejected:** Separate terminal sessions by hand. An Agent SDK script.
- **Consequences:** Worktrees do not contain gitignored files, so the venv is referenced by absolute path. Old worktrees and their branches stay until cleaned up (see [run-book known gaps](run-book.md#known-gaps)).
- **Landed:** `b8b0294`. Setup in [docs/agents.md](../agents.md).

## ADR-010: Save the user message before the RAG call

- **Context:** The RAG call takes up to 120 seconds and can fail in several ways. The user's question should not be lost when it does.
- **Decision:** `ChatService` saves the user message first, then calls rag-service. If the call fails, the user message stays and the error carries `conversationId`. No database transaction spans the rag call, so the database is not held open for up to 120 seconds.
- **Rejected:** One transaction around the whole request. Saving only after a successful answer.
- **Consequences:** A failed answer leaves a user message with no reply, which is what the transcript should show. The client must keep the returned `conversationId` (see [ADR-011](#adr-011-conversationid-on-503-504-and-500)).
- **Landed:** `1131c7d`. Documented in the `ChatService` class comment.

## ADR-011: conversationId on 503, 504 and 500

- **Context:** After a failed answer the user message is saved. Without the id the client cannot continue the same transcript.
- **Decision:** Errors from the chat step carry `conversationId` once the conversation exists: `503`, `504` and `500`. It is omitted for `400` and `404`. The frontend keeps the id on those three statuses.
- **Rejected:** Returning the id only on success. Creating a new conversation after a failure, which would split the transcript.
- **Consequences:** The contract changed twice. `06a65c5` added it to 503 and 504. `ce192d9` added it to 500, after rag-service's own 500 and 422 were included in the 500 row. Backend `2413a12` and frontend `121f813` implement the second change.
- **Landed:** `06a65c5`, `ce192d9`, `2413a12`, `121f813`. History in [contract-history.md](contract-history.md).

## ADR-012: rag-service errors map to 500 for the browser

- **Context:** rag-service returns `500` (chain failed) and `422` (invalid body). The browser should not see rag-service's details.
- **Decision:** Any rag-service error other than `503` (index not ready) maps to the backend's `500`, with a generic message. `503` maps to `503`. A read timeout maps to `504`.
- **Rejected:** Passing rag-service's status codes through.
- **Consequences:** The browser sees a small set of statuses. Details are only in logs.
- **Landed:** `2413a12` (`RagClient` maps to `RagFailedException`; `ChatService` maps that to `FAILED`). Contract row for `500` in `ce192d9`.

## ADR-013: Timeouts: 120s read to rag-service, 5s connect

- **Context:** A local model can take 10 to 60 seconds per answer. A missing service should fail fast.
- **Decision:** `rag.service.read-timeout` defaults to `120s`. The connect timeout is fixed at 5 seconds in `RagConfig`. A read timeout becomes `504`, a connect failure becomes `503`.
- **Rejected:** One short timeout for both. No timeout.
- **Consequences:** A slow answer waits up to two minutes. The UI shows a spinner for that time.
- **Landed:** `1131c7d`. Plan decision in [section 3, gap 6](../implementation-plan.md#3-gaps-found-in-the-original-outline).

## ADR-014: Prompts as constants with builder functions

- **Context:** The prompts lived inside `PDFRAGApp` methods. They needed to be tested and version controlled.
- **Decision:** Prompts are named string constants in `rag-service/prompts.py`, with `query_rewrite_prompt()` and `rag_answer_prompt()` builders. Each prompt has a test that checks its input variables. The answer prompt includes a guardrail: say so when the context does not answer the question.
- **Rejected:** YAML, Jinja or a database. Overkill for two prompts.
- **Consequences:** Adding a prompt means adding a constant, a builder and a test.
- **Landed:** `d065033`.

## ADR-015: Sync `def` for `/ask`

- **Context:** The chain call blocks for seconds.
- **Decision:** `/ask` is a plain `def`, so FastAPI runs it in its threadpool and the event loop stays free for `/health`.
- **Rejected:** `async def` with a blocking call inside it.
- **Consequences:** Concurrent requests can run in parallel threads. Whether the shared chain is safe under concurrent calls was not tested.
- **Landed:** `d065033` (`rag-service/app.py`).

## ADR-016: Conversation id in sessionStorage

- **Context:** A reload should restore the transcript. Two tabs should not share one conversation by accident.
- **Decision:** Store the id in `sessionStorage` under `chat.conversationId`. Every read and write is wrapped in try/catch, so the page works when storage is blocked.
- **Rejected:** `localStorage` (shared across tabs). Keeping the id only in memory (lost on reload).
- **Consequences:** A new tab starts a new conversation. Storage errors are silent; the page still works for the current load.
- **Landed:** `3a6e764` (`frontend/src/storage.js`).

## ADR-017: Vite dev proxy instead of CORS

- **Context:** The browser calls `/api` on port 5173. The backend is on 8080.
- **Decision:** `vite.config.js` proxies `/api` to `http://localhost:8080`. The backend has no CORS configuration.
- **Rejected:** CORS on the backend. A reverse proxy in front of both.
- **Consequences:** Development needs no CORS setup. Any production setup must serve `/api` from the same origin.
- **Landed:** `3a6e764`. Plan decision in [section 9](../implementation-plan.md#9-phase-1c--frontend-frontend-dev).

## ADR-018: Tests never need Ollama, the network or other modules

- **Context:** Agents work in parallel and cannot start the full stack.
- **Decision:** Each module's tests mock its outside calls. rag-service mocks Ollama and PDF loading. The backend mocks rag-service (`MockRestServiceServer`). The frontend mocks `fetch`. Full-stack checks are a separate Phase 2 step.
- **Rejected:** Shared integration tests in each module.
- **Consequences:** Unit suites run in seconds on any machine. The full path through a real model is only checked by the Phase 2 smoke test, which is not yet written.
- **Landed:** Rule in [CLAUDE.md](../../CLAUDE.md); tests in `d065033`, `1131c7d`, `3a6e764`.

## ADR-019: One shared virtualenv at the repo root

- **Context:** rag-service is the only Python module, and the venv is slow to rebuild.
- **Decision:** `.venv/` at the repo root, gitignored. Worktrees reference it by absolute path. Agents do not run `pip install`.
- **Rejected:** A venv per worktree.
- **Consequences:** Dependencies must be installed once by the main session. `.claude/settings.json` denies `pip install` for agents.
- **Landed:** Plan, Phase 0 (`b8b0294`).
