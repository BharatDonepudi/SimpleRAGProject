# Run book

How to start the stack, check each process, and read the common failures. Module READMEs have the full detail: [rag-service](../../rag-service/README.md), [backend](../../backend/README.md), [frontend](../../frontend/README.md). The HTTP status meanings are in [api-contract.md](../api-contract.md).

## Prerequisites

| Need | Check | Used by |
|---|---|---|
| Ollama running on `:11434` | `curl -s localhost:11434/api/tags` (lists models) | rag-service |
| Models `gemma4` and `nomic-embed-text` | `ollama list` | rag-service |
| Python venv at the repo root (`.venv/`) | `/Users/keerthikambhampati/gitRepo/LLMUsingApi/.venv/bin/python --version` | rag-service |
| JDK 21 | `java -version` | backend (the Maven wrapper is bundled, no Maven install) |
| Node 20 or newer (the project uses 24) | `node -v` | frontend |
| `rag-service/data/HOA.pdf` | `ls rag-service/data/HOA.pdf` | rag-service |
| Python packages import cleanly in the venv | `../.venv/bin/python -c "import chromadb, langchain_community"` prints nothing | rag-service (see the chromadb row under common failures) |

Setup commands:

```bash
ollama pull gemma4
ollama pull nomic-embed-text
cd frontend && npm install          # once
```

The venv is gitignored. Git worktrees do not contain it, so use the absolute path to `.venv/bin/python` there ([CLAUDE.md](../../CLAUDE.md)).

## Start order

Start each process in its own terminal. Order matters because each layer needs the one below it to be up.

1. **Ollama.** Start the app or run `ollama serve`. Confirm the two models are listed.
2. **rag-service** (from `rag-service/`, because `./data/HOA.pdf` is relative to it):
   ```bash
   cd rag-service
   ../.venv/bin/uvicorn app:app --port 8000
   ```
   The index build takes a while. See [known gaps](#known-gaps) for why `/health` may not answer during the build. Once it does:
   ```bash
   curl -s localhost:8000/health        # {"status":"ok","chunks":N}
   ```
3. **Backend** (from `backend/`, because the H2 path `./data/chatdb` is relative to it):
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   ```bash
   curl -s localhost:8080/actuator/health   # {"groups":[...],"status":"UP"}
   ```
4. **Frontend** (from `frontend/`):
   ```bash
   cd frontend
   npm run dev
   ```
   Open http://localhost:5173. Ask "What are the main points that I should refer to first". The page shows "Thinking" and then the answer. A reload restores the transcript in the same tab.

Optional check of the backend without rag-service: `POST /api/chat` returns `503` with a `conversationId`, and the user message is saved. (Checked on 2026-10-06 by starting the backend with an in-memory H2 URL and no rag-service.)

## Health checks

| Process | Check | Healthy looks like |
|---|---|---|
| Ollama | `curl -s localhost:11434/api/tags` | JSON listing `gemma4` and `nomic-embed-text` |
| rag-service | `curl -s localhost:8000/health` | `{"status":"ok","chunks":N}`, N > 0 |
| Backend | `curl -s localhost:8080/actuator/health` | `"status":"UP"` |
| Backend API | `curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"conversationId":null,"message":"hi"}'` | `200` with `conversationId`, `answer`, `createdAt` (needs rag-service up) |
| Frontend | open http://localhost:5173 | chat page with the input box |

## Tests (no Ollama, network or other services needed)

| Module | Command | Expected |
|---|---|---|
| rag-service | `cd rag-service && ../.venv/bin/python -m unittest discover -s tests -v` | 22 tests, OK |
| backend | `cd backend && ./mvnw test` | 48 tests, 0 failures |
| frontend | `cd frontend && npm test -- --run` | 19 tests passed |
| frontend build | `cd frontend && npm run build` | output in `dist/` |

Single test: `./mvnw test -Dtest=ChatServiceTest`, `npx vitest run src/App.test.jsx`, or `../.venv/bin/python -m unittest tests.test_app -v`.

## Common failures

These are what each symptom means. The statuses come from [Contract A](../api-contract.md) and [Contract B](../api-contract.md#contract-b--backend--rag-service-fastapi-port-8000).

| Symptom | What it means | What to do |
|---|---|---|
| Chat shows "The document assistant is not ready yet" (backend `503`) | The backend could not reach rag-service, or rag-service is still building its index. The user message is saved and the body has `conversationId`. | Check `curl localhost:8000/health`. If it refuses the connection, rag-service is still starting or is not running. Wait, or start it. |
| rag-service connection refused for minutes after start | The index is still being built. uvicorn does not open the port until startup finishes (see [known gaps](#known-gaps)). | Watch the uvicorn log for the "Application startup complete" line. Do not expect `"loading"` from `/health` during the build. |
| Chat shows "took too long to answer" (backend `504`) | rag-service did not answer within `rag.service.read-timeout` (default 120s). Usually a slow model. | Check that Ollama is not overloaded. Raise `rag.service.read-timeout` only if the model is genuinely slow. |
| Chat shows "could not answer" (backend `500`) | rag-service returned an error (its `500` or `422`), or the backend hit an unexpected error. | Check the rag-service log for `chain failed` and the backend log for `Unexpected failure in conversation`. Most often Ollama stopped or a model is missing. |
| rag-service `/health` shows `"status":"error"` with `detail` "Could not import chromadb python package" | Observed on 2026-10-06 in the shared `.venv`. The real error is an `ImportError`: `cannot import name '_ExtendedAttributes' from 'opentelemetry.util.types'`. `chromadb` is installed, but its OpenTelemetry dependency does not match. LangChain re-raises it with the generic message. | Fix the packages in the shared venv (main session; agents may not `pip install`). Then restart rag-service. Until then no `/ask` can succeed. |
| rag-service `/health` shows `"status":"error"` with a `detail` | Startup failed. The `detail` says why, for example the PDF path or a failed model pull. | Read `detail`. If the PDF is not found, start rag-service from `rag-service/`. If the pull failed, check Ollama and `ollama list`. (Code path only; not triggered on purpose during this review.) |
| rag-service `/ask` returns `503` with `index unavailable: ...` | Same cause as `error` above, seen through `/ask`. | Same as above. |
| Backend `404` on the messages endpoint, and the page forgets the conversation | The stored id is unknown to the backend, for example after deleting `backend/data/`. | The page clears the id and starts a new conversation on the next send. Nothing to do. |
| Backend `400` "Malformed request body" or "Invalid request" | Bad JSON, a blank message, or a message over 2000 characters after trimming. | Fix the request. |
| Backend `415` or `405` | Wrong `Content-Type` or method. | Send `Content-Type: application/json` with `POST`. |
| Page shows errors on every send | Backend not on `:8080`. The Vite proxy forwards `/api` there. | Start the backend, then retry. |
| Port already in use (`8000`, `8080`, `5173`) | An earlier process is still running. | Find it with `lsof -i :8000` (or the port) and stop it. |
| rag-service "PDF not found" or a wrong PDF | The default path `./data/HOA.pdf` is relative to the working directory. | Start from `rag-service/`, or set `RAG_DOC_PATH`. |
| A new `data/` folder appears at the repo root | The backend was started from the root, so H2 wrote `./data/chatdb` there. Only `backend/data/` is gitignored. | Delete the root `data/` folder and start the backend from `backend/`. |

## Known gaps

Found while writing this run book. Gaps 4–7 were fixed on 2026-10-06 by the main session; the root README got an interim rewrite, and Phase 2 still adds the smoke test to it. Owners are listed so the right agent or session can fix each one. Contract changes go through the main session.

| # | Gap | Evidence | Owner |
|---|---|---|---|
| 1 | The index is built inside the FastAPI lifespan, which uvicorn runs before it binds the port. So `/health` cannot report `"loading"` while the index builds, and clients see connection refused. The `503` from `/ask` for a loading index is therefore not reachable over HTTP at startup. The backend still returns `503` for that case (connection refused maps to `RagUnavailableException`). | `rag-service/app.py` (`await run_in_threadpool(rag.build_index)` in `lifespan`). In the installed uvicorn, `Server.startup()` calls `await self.lifespan.startup()` before `loop.create_server`. Read from source, and confirmed live on 2026-10-06: `/health` refused the connection 3 seconds after start, while the build was still running. | rag-service-dev. The contract text for `/health` and `503` also assumes a loading state the server cannot show; a contract change goes through the main session. |
| 2 | The `app.py` module docstring says the index is built "in a background thread", and the rag-service README says `/health` reports progress. Both differ from the code in gap 1. | `rag-service/app.py` lines 3-5; `rag-service/README.md` "Run" section. | rag-service-dev |
| 3 | `rag-service/requirements.txt` lists `pdfplumber` twice. Several listed packages are not imported by any file under `rag-service/`: `pdfplumber`, `elevenlabs`, `sentence-transformers`, `fastembed`, `unstructured`, `requests`. Whether they are needed as transitive dependencies was not checked. | `grep` of `import` lines in `rag-service/*.py` and `tests/*.py`. | rag-service-dev |
| 4 | The backend README says the `503` and `504` bodies carry `conversationId`, but it does not say `500` does too. The contract and code do. | `backend/README.md`, "Endpoints" section; `GlobalExceptionHandler` maps `FAILED` with the id. | backend-dev **Fixed 2026-10-06.** |
| 5 | Root `CLAUDE.md` still lists rag-service, backend and frontend as "pending". Its rag-service section describes the old `run()` pipeline and says the test file loads by `importlib`; the tests now use a `sys.path` insert. | `CLAUDE.md` status table and "rag-service/pdf_rag.py architecture" section; `rag-service/tests/test_pdf_rag.py` top lines. | Main session **Fixed 2026-10-06.** |
| 6 | `docs/implementation-plan.md` status table still shows phases 1A, 1B and 1C as "Not started". | Status table at the top of the file. | Main session **Fixed 2026-10-06.** |
| 7 | Root `README.md` still describes the single script and lists packages from before the refactor. It is due to be rewritten in Phase 2. | `README.md`. | integration-tester (Phase 2) **Fixed 2026-10-06.** |
| 8 | `scripts/` and `scripts/smoke.sh` do not exist yet. Phase 2 is not started. | `ls` of the repo root. | integration-tester (Phase 2) |
| 9 | Six git worktrees exist under `.claude/worktrees/` (one locked: `agent-acdd7b1c3a15d0f2f`). Their `worktree-agent-*` branches and `feature/rag-service-phase1a` remain after merge. | `git worktree list`. | Main session |
| 10 | Two commits on worktree branches (`51edb5f`, `ae0dff0`) are superseded attempts at Phases 1B and 1C and are not on this branch. | `git merge-base --is-ancestor`. See [CHANGELOG](../CHANGELOG.md#not-on-this-branch). | Main session |
| 11 | `starter-1.py` and `start-2.py` at the repo root are untracked, although the plan says they stay at the root. `docs/agents.md` has uncommitted edits. | `git status`. | Main session |
| 12 | The shared `.venv` cannot import `chromadb` (an OpenTelemetry version mismatch, seen 2026-10-06). rag-service therefore starts with `status: error` in this environment, and the `/ask` answer path cannot be run here. The unit tests pass because they mock the pipeline steps and never reach this import. | Live run of `uvicorn app:app` from `rag-service/` on 2026-10-06. | Main session (venv); rag-service-dev if the requirements need a pin |
| 13 | Phase 2 has not run here. There is no recorded full-stack run and no smoke test. The answer path through a real Ollama model is not verified in these docs. | Plan status table; no `scripts/`. | integration-tester (Phase 2) |
