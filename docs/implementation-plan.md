# Implementation Plan: PDF Chat (React → Spring Boot → Python RAG)

| | |
|---|---|
| Branch | `feature/chat-rag-app` |
| Approved | 2026-10-05 |
| Related | [API contract](api-contract.md) · [Agents guide](agents.md) · [Agent study notes](agent-notes.md) · [Knowledge base](knowledge-base/README.md) · [Changelog](CHANGELOG.md) |

## Status

| Phase | What | Owner | Status |
|---|---|---|---|
| 0 | Foundation: restructure, contract, agents, permissions | main session | ✅ Done (`b8b0294`) |
| 1A | rag-service: `prompts.py`, `pdf_rag.py` refactor, FastAPI `app.py` | `rag-service-dev` | ✅ Done (`d065033`, merged `58c163e`) |
| 1B | backend: Spring Boot REST API + H2 transcript log | `backend-dev` | ✅ Done (`1131c7d`, `2413a12`, merged `25c7cab`) |
| 1C | frontend: React chat page | `frontend-dev` | ✅ Done (`3a6e764`, `121f813`, merged `7714c62`) |
| 2 | Integration: merge, all suites, full stack, smoke test, root docs | `integration-tester` | ⏳ Not started |

Phases 1A–1C ran in parallel and are merged into `feature/chat-rag-app`. On the merged tree the unit suites pass: rag-service 22, backend 48, frontend 19. Contract A changed during Phase 1 (`06a65c5`, `ce192d9`): 503, 504 and 500 now carry `conversationId`, and 405 and 415 are listed.

Fixed before Phase 2 (2026-10-06):
- The shared `.venv` couldn't import `chromadb`: installing `fastapi` had raised `opentelemetry-api` to 1.45.0 while `opentelemetry-sdk` stayed at 1.41.0. The OpenTelemetry packages were upgraded to 1.45.0; rag-service now starts with status `ok` (133 chunks).
- `app.py` awaited the index build in the lifespan, so uvicorn didn't bind `:8000` until it finished. The build now runs in a background thread and `/health` reports `loading` meanwhile.

---

## 1. Context

`rag-service/pdf_rag.py` answers one hard-coded question from the command line. It also rebuilds the whole Chroma index on every run. The goal is a browser chat where users can ask questions about `HOA.pdf`.

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    │
                                    └── H2 file DB (transcript log)
```

## 2. Decisions

| Decision | Chosen | Why | Rejected alternative |
|---|---|---|---|
| How Spring reaches the RAG code | Long-running Python HTTP service (FastAPI) | Index is built once at startup instead of re-embedding the PDF for every question | Subprocess per request: slow, fragile error handling |
| Memory store | H2 file database | Embedded in Spring, real SQL, no server, can be swapped for Postgres later | Excel: can't handle two writes at once. SQLite: needs a community dialect. JSON: not queryable |
| What memory is for | Transcript log only | Keeps the RAG chain unchanged for v1 | History fed back as context (possible later) |
| Answer delivery | Full answer + "Thinking…" spinner | Simplest and most robust across three layers | Token streaming (SSE in every layer) |
| Repo layout | Monorepo | One clone; each agent owns one folder | Separate repos |
| Backend build | Maven (`./mvnw`) | Most common for Spring Boot | Gradle |
| Frontend language | Plain JavaScript | Keeps the scaffold simple | TypeScript |
| Parallel development | Claude Code subagents, each in its own git worktree | No extra setup; runs from one session | Separate terminal sessions; Agent SDK script |

## 3. Gaps found in the original outline

1. A script started for each request would re-embed the PDF every time. → Long-running service.
2. Excel isn't safe for concurrent writes. → H2.
3. There was no API contract, so the modules couldn't be built in parallel. → `docs/api-contract.md`, written first.
4. `pdf-rag.py` couldn't be imported because of the hyphen. → Renamed `pdf_rag.py`.
5. Worktrees only contain committed files. `tests/` and `CLAUDE.md` were untracked, there was no `.gitignore`, and `.venv` doesn't exist inside a worktree. → Phase 0 committed everything; agents use the absolute venv path.
6. Local `gemma4` answers take 10–60s. → Timeouts in every layer (backend read timeout 120s) and a loading state in the UI.

## 4. Tools (checked 2026-10-05)

| Tool | Version | Needed by |
|---|---|---|
| Node / npm | 24.14 / 11.9 | frontend |
| Java (OpenJDK) | 21.0.10 | backend |
| Maven | 3.9.8 installed; the backend uses the `./mvnw` wrapper (3.9.16) | backend |
| Spring Boot | 4.1.1 (from Initializr) | backend |
| Python (`.venv`) | 3.13.1, with fastapi 0.142.2, uvicorn, httpx | rag-service |
| Ollama | 0.33.3 with `gemma4` and `nomic-embed-text` | rag-service (runtime), Phase 2 |
| git | 2.54 | worktrees |
| Playwright browsers | not installed | optional E2E only |

Network access is needed during implementation for the npm registry, Maven Central, PyPI and start.spring.io.

## 5. Target layout

```
LLMUsingApi/
├── .gitignore
├── README.md                 # rewritten in Phase 2
├── CLAUDE.md
├── docs/                     # contract, plan, agents guide, study notes
├── .claude/
│   ├── settings.json         # command allow/deny list for agents
│   └── agents/               # 4 agent definitions
├── rag-service/              # Python: pdf_rag.py, prompts.py, app.py, tests/, data/HOA.pdf
├── backend/                  # Spring Boot (Maven, Java 21)
├── frontend/                 # React + Vite
└── scripts/smoke.sh          # Phase 2
```

`starter-1.py` and `start-2.py` stay at the root. They are standalone Ollama demos and are not part of the app.

---

## 6. Phase 0 — Foundation ✅

Done in commit `b8b0294`:
- Moved `pdf-rag.py` → `rag-service/pdf_rag.py`, `data/` → `rag-service/data/`, `requirements.txt` → `rag-service/` (+ `fastapi`, `uvicorn`, `httpx`). Moved `tests/` into `rag-service/tests/`.
- Wrote `docs/api-contract.md` (Contract A: frontend → backend; Contract B: backend → rag-service).
- Added the four agent definitions and `.claude/settings.json`.
- Added `.gitignore` and `CLAUDE.md`; fixed paths in `README.md`.
- Installed `fastapi` into `.venv`.
- The two existing tests pass.

## 7. Phase 1A — rag-service (`rag-service-dev`)

**`prompts.py`: approach.** Prompts are plain Python string constants plus small builder functions. They're version-controlled, need no extra dependencies, and can be unit-tested. YAML, Jinja or a database would be overkill for two prompts.
- Move the templates out of `PDFRAGApp.create_query_prompt()` and `create_rag_prompt()` into:
  - `QUERY_REWRITE_TEMPLATE` and `RAG_ANSWER_TEMPLATE`
  - `query_rewrite_prompt() -> PromptTemplate` and `rag_answer_prompt() -> ChatPromptTemplate`
- Add a guardrail sentence: if the context doesn't contain the answer, say so instead of inventing one.
- Rule for later: every new prompt is a named constant in this file plus a test that checks its `input_variables`.

**`pdf_rag.py` refactor**
- `RAGConfig` defaults can be overridden with env vars `RAG_DOC_PATH`, `RAG_MODEL` and `RAG_EMBED_MODEL`.
- Split `run()` into:
  - `build_index()`: load → split → vector DB → chain, stored on the instance, with the chunk count recorded.
  - `answer(question) -> str`
- `run()` and `main()` stay as a CLI wrapper.
- `load_documents` raises `FileNotFoundError` instead of `print` + `SystemExit`.

**`app.py` (FastAPI)**
- A lifespan hook builds the index once at startup. Status goes from `loading` to `ok`, or to `error` with a detail.
- `/ask` is a sync `def`, so the blocking chain call runs in FastAPI's threadpool. It returns `503` until the index is ready.
- `/health` returns the status and chunk count.
- Run with `../.venv/bin/uvicorn app:app --port 8000` from `rag-service/`.

**Tests** (stdlib `unittest`; no Ollama, no real PDF)
- `test_pdf_rag.py`: switch to a normal import; keep the existing tests; add `build_index`/`answer` tests.
- `test_prompts.py`: checks each template's input variables.
- `test_app.py`: `TestClient` covering 200, 503 while loading, 422 for an invalid body, and `/health`.

**README:** venv, install, Ollama pulls, run command, test commands, env vars.

## 8. Phase 1B — backend (`backend-dev`)

**Project generation.** Spring Initializr with `web, data-jpa, h2, validation, actuator`, Java 21, group `com.llmusingapi`. Use whatever stable Spring Boot version Initializr offers; don't pin a guessed one. Commit the `./mvnw` wrapper.

**Code** (package `com.llmusingapi.backend`)

| Package | Contents |
|---|---|
| `chat/` | `ChatController` (`POST /api/chat`, `GET /api/conversations/{id}/messages`), `ChatService`, DTO `record`s with `@NotBlank @Size(max = 2000)` |
| `rag/` | `RagClient` on Spring `RestClient`; `rag.service.url` (default `http://localhost:8000`), `rag.service.read-timeout` (default 120s); connection errors and rag-service 503s become `RagUnavailableException`, timeouts become `RagTimeoutException` |
| `persistence/` | `Conversation` (UUID, createdAt), `Message` (role USER/ASSISTANT, `@Lob` content, createdAt) + repositories |
| `web/` | `GlobalExceptionHandler`: `{ "error": "..." }` with 400 / 404 / 503 / 504 / 500 |

- `ChatService` saves the user message **before** calling rag-service, so it's logged even if the call fails.
- `application.properties`:
  - `server.port=8080`
  - `spring.datasource.url=jdbc:h2:file:./data/chatdb`
  - `ddl-auto=update`
  - H2 console enabled
- **CORS:** not needed in development, because the Vite proxy forwards `/api`.

**Tests** (JUnit 5, Mockito, MockMvc; no network)

| Test | Covers |
|---|---|
| `ChatControllerTest` (`@WebMvcTest`) | Validation 400s, happy path, error mapping |
| `ChatServiceTest` | Message order; user message kept when the RAG call fails |
| `RagClientTest` (`MockRestServiceServer`) | 200, 503, timeout |
| `MessageRepositoryTest` (`@DataJpaTest`) | Persistence |

**README:** JDK check, `./mvnw spring-boot:run`, `./mvnw test`, a single test (`-Dtest=ChatServiceTest`), H2 console, config keys.

## 9. Phase 1C — frontend (`frontend-dev`)

**Setup.** `npm create vite@latest frontend -- --template react`. Dev deps: `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`. `vite.config.js` proxies `/api` → `:8080` and uses a `jsdom` test environment.

**Components**

| Component | Responsibility |
|---|---|
| `App` | State: `messages[]`, `conversationId`, `pending`, `error` |
| `ChatWindow` | Scrollable bubbles, auto-scroll, "Thinking…" bubble, error bubble |
| `MessageInput` | Textarea + Send; Enter sends, Shift+Enter adds a new line; disabled while pending; ignores blank input |
| `api/chatClient.js` | `sendMessage`, `getMessages` via `fetch`; throws the server's `error` text |

`conversationId` is kept in `sessionStorage` (every access wrapped in try/catch). On reload, history is restored from `GET /api/conversations/{id}/messages`; a 404 clears it.

**Tests** (Vitest + React Testing Library, `fetch` mocked)
- Empty chat renders.
- Send → user bubble → spinner → answer.
- Input disabled while pending.
- A 503 shows the error.
- Blank input isn't sent.
- `chatClient` sends the right request.

**README:** Node check, `npm install`, `npm run dev`, `npm test`, a single test file, `npm run build`.

## 10. Phase 2 — Integration (`integration-tester`)

1. Merge the three agent branches one at a time. They shouldn't conflict, since each touches only its own folder.
2. Run every unit suite.
3. Start the stack in this order:
   1. Ollama
   2. rag-service (wait for `/health` to return `ok`)
   3. backend (wait for `/actuator/health`)
   4. frontend
4. Run `scripts/smoke.sh`. It checks:
   - `/health` is ok
   - chat returns an answer and an id
   - history has 2 messages
   - a blank message returns 400
   - the error shape is correct
5. Manual check: stop rag-service, send one message, expect 503, restart.
6. Optional: one Playwright E2E test (`npx playwright install chromium` first).
7. Rewrite the root `README.md` and update `CLAUDE.md`.

The integration tester doesn't edit module code. It reports defects to the module's owner.

---

## 11. Test plan

Tests are written in the same phase as the code they cover. An agent isn't done until its suite passes.

| Layer | Tools | Phase | Needs Ollama? |
|---|---|---|---|
| rag-service unit/API | unittest + FastAPI TestClient | 1A | No |
| backend unit/slice | JUnit 5, Mockito, MockMvc, MockRestServiceServer, @DataJpaTest | 1B | No |
| frontend components | Vitest + RTL | 1C | No |
| Contract conformance | Each module's tests use the JSON shapes from `api-contract.md` verbatim | 1 | No |
| Full-stack smoke | `scripts/smoke.sh` | 2 | Yes |
| E2E (optional) | Playwright | 2 | Yes |

## 12. Agents

Four subagents are defined in `.claude/agents/`:

| Agent | Owns | Works in |
|---|---|---|
| `rag-service-dev` | `rag-service/` | its own worktree |
| `backend-dev` | `backend/` | its own worktree |
| `frontend-dev` | `frontend/` | its own worktree |
| `integration-tester` | `scripts/`, root `README.md`, `CLAUDE.md` | main checkout, after merge |

**Ground rules** (stated in each agent file)
- Edit only the owned folder.
- Never edit the contract; if it looks wrong, stop and report.
- Tests can't depend on Ollama, the network or other modules.
- Commit on the agent's own branch and never push.
- Finish with a structured report.

**Dependencies and access**
- Claude Code and git.
- Phase 0 committed (worktrees branch from HEAD).
- Network access to package registries.
- `.claude/settings.json` allowlist: npm, npx, `./mvnw`, the venv's python/uvicorn, local curl, local git. **Denied:** `git push` and `pip install` into the shared venv.
- Ollama running, needed only for Phase 2.

Launch, review, merge and follow-up prompts are in [agents.md](agents.md).

## 13. Risks

| Risk | Mitigation |
|---|---|
| Agents read the contract differently | Exact JSON examples in the contract; tests use them verbatim; Phase 2 smoke test |
| An agent edits outside its folder | Ownership rule in its prompt; review the diff before merging |
| Shared `.venv` changed by an agent | Deps installed in Phase 0; `pip install` denied |
| Port clashes between agents | Phase 1 runs unit tests only; only Phase 2 starts servers |
| Slow local model causes timeouts | 120s backend read timeout; spinner in the UI |
| Agent reports success that isn't real | Rerun the suites yourself before merging |
| Chroma rebuilds the index on every rag-service restart | Acceptable for v1; persisting the Chroma directory is a later improvement |

## 14. Verification (definition of done)

1. `cd rag-service && ../.venv/bin/python -m unittest discover -s tests -v` passes.
2. `cd backend && ./mvnw test` passes.
3. `cd frontend && npm test -- --run && npm run build` passes.
4. With the stack running, `curl localhost:8000/health` returns `"status": "ok"`.
5. `scripts/smoke.sh` exits 0.
6. At http://localhost:5173, asking "What are the main points that I should refer to first" shows the spinner, then an answer. A page reload restores the history.
7. The H2 console (`/h2-console`) shows the user and assistant rows in `MESSAGE`.

## 15. Later (out of scope for v1)

- Feed conversation history into the RAG chain so follow-up questions work.
- Stream tokens (SSE).
- Persist the Chroma index to disk.
- Upload or select other PDFs.
- Move from H2 to Postgres.
