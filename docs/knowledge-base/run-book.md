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
cd rag-service && ../.venv/bin/pip install -r requirements.txt   # once; versions are pinned
cd frontend && npm install          # once
```

The venv is gitignored. Git worktrees do not contain it, so use the absolute path to `.venv/bin/python` there ([CLAUDE.md](../../CLAUDE.md)). No worktrees exist at the moment; they were removed after Phase 1.

## Start order

Start each process in its own terminal. Order matters because each layer needs the one below it to be up.

1. **Ollama.** Start the app or run `ollama serve`. Confirm the two models are listed.
2. **rag-service** (from `rag-service/`, because `./data/HOA.pdf` is relative to it):
   ```bash
   cd rag-service
   ../.venv/bin/uvicorn app:app --port 8000
   ```
   The port opens after the imports (a few seconds). The index is built in a background thread ([ADR-020](decisions.md#adr-020-build-the-index-in-a-background-thread)), so `/health` answers at once with `"loading"` and then `"ok"`. In the Phase 2 run it reached `ok` with 133 chunks in about 11 seconds. Wait for `ok` before starting the backend:
   ```bash
   curl -s localhost:8000/health        # {"status":"loading","chunks":0}, then {"status":"ok","chunks":133}
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
   Open http://localhost:5173. Ask "What are the main points that I should refer to first". The page shows "Thinking" and then the answer (8–22 seconds in the Phase 2 runs). A reload restores the transcript in the same tab.
5. **Smoke test** (from the repo root, with the stack running):
   ```bash
   scripts/smoke.sh
   ```
   It prints `PASS` or `FAIL` per check and exits non-zero on the first failure. The six checks: rag-service `/health` is `ok` with chunks > 0; backend `/actuator/health` is `UP`; `POST /api/chat` with one real question returns 200, a non-blank `answer`, `createdAt` and a UUID `conversationId`; the transcript for that id has two messages, `user` then `assistant`; a blank message returns `400` with `{"error": ...}` and no `conversationId`; an unknown conversation returns `404` with `{"error": ...}`. Override with `RAG_URL`, `BACKEND_URL`, `QUESTION` and `CHAT_TIMEOUT` (default 180 seconds). It needs `curl` and `python3`. It does not test the 503 path; do that by hand (below).

Manual 503 check (done in Phase 2 on 2026-10-06): stop rag-service and send a message. `POST /api/chat` returns `503` with a `conversationId`, and the user message is in the transcript. Start rag-service again, wait for `ok`, and retry in the same conversation: it returns `200` with the same id.

## Health checks

| Process | Check | Healthy looks like |
|---|---|---|
| Ollama | `curl -s localhost:11434/api/tags` | JSON listing `gemma4` and `nomic-embed-text` |
| rag-service | `curl -s localhost:8000/health` | `{"status":"ok","chunks":N}`, N > 0 (133 for `HOA.pdf`). `"loading"` with 0 chunks while the index builds. |
| Whole stack | `scripts/smoke.sh` from the repo root | Six `PASS` lines and `Smoke test passed.` |
| Backend | `curl -s localhost:8080/actuator/health` | `"status":"UP"` |
| Backend API | `curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"conversationId":null,"message":"hi"}'` | `200` with `conversationId`, `answer`, `createdAt` (needs rag-service up) |
| Frontend | open http://localhost:5173 | chat page with the input box |

## Tests (no Ollama, network or other services needed)

| Module | Command | Expected |
|---|---|---|
| rag-service | `cd rag-service && ../.venv/bin/python -m unittest discover -s tests -v` | 25 tests, OK |
| backend | `cd backend && ./mvnw test` | 48 tests, 0 failures |
| frontend | `cd frontend && npm test -- --run` | 19 tests passed |
| frontend build | `cd frontend && npm run build` | output in `dist/` |

Totals re-run on 2026-10-06 at `be3570f`. The full-stack check is `scripts/smoke.sh` (step 5 above); it needs the running stack and Ollama, so it is not a unit test.

Single test: `./mvnw test -Dtest=ChatServiceTest`, `npx vitest run src/App.test.jsx`, or `../.venv/bin/python -m unittest tests.test_app -v`.

## Common failures

These are what each symptom means. The statuses come from [Contract A](../api-contract.md) and [Contract B](../api-contract.md#contract-b--backend--rag-service-fastapi-port-8000).

| Symptom | What it means | What to do |
|---|---|---|
| Chat shows "The document assistant is not ready yet" (backend `503`) | The backend could not reach rag-service, or rag-service is still building its index (`/ask` returns 503 `index loading`), or its build failed. The user message is saved and the body has `conversationId`. | Check `curl localhost:8000/health`. `loading`: wait. `error`: read `detail`. Connection refused: rag-service is not running (or is still importing). After it is `ok`, send again; the page keeps the same conversation. |
| rag-service connection refused a few seconds after start | uvicorn is still importing LangChain (a few seconds). Since `864d09c` the index build does not hold up the port; `/health` reports `"loading"` while it runs. | Wait a few seconds and retry. If it stays refused, check the uvicorn log for an import error. |
| Blank or missing answer in the browser | Before `2063d33`: `gemma4`'s hidden reasoning used up the token budget (`done_reason=length`), rag-service returned 200 with `"answer": ""`, and the page showed an empty bubble. Since `2063d33`, a blank answer is a rag-service `500`, so the page shows the "could not answer" error instead, and the id is kept. Answers that take 40 seconds or more point the same way. | Check `RAGConfig.reasoning` is `False` in `rag-service/pdf_rag.py`, and look for `chain returned an empty answer` in the rag-service log. Retry the question. If you changed `RAG_MODEL`, the new model may need a different `reasoning` setting. See [ADR-022](decisions.md#adr-022-model-reasoning-off-and-a-blank-answer-is-a-500). |
| Chat shows "took too long to answer" (backend `504`) | rag-service did not answer within `rag.service.read-timeout` (default 120s). Usually a slow model. | Check that Ollama is not overloaded. Raise `rag.service.read-timeout` only if the model is genuinely slow. |
| Chat shows "could not answer" (backend `500`) | rag-service returned an error (its `500` or `422`), or the backend hit an unexpected error. rag-service's `500` covers a chain exception and, since `2063d33`, a blank answer. | Check the rag-service log for `chain failed` or `chain returned an empty answer`, and the backend log for `Unexpected failure in conversation`. Most often Ollama stopped or a model is missing. |
| rag-service `/health` shows `"status":"error"` with `detail` "Could not import chromadb python package" | Observed on 2026-10-06 in the shared `.venv`, before `be52f79`. The real error is an `ImportError`: `cannot import name '_ExtendedAttributes' from 'opentelemetry.util.types'`. `chromadb` is installed, but its OpenTelemetry dependency does not match. LangChain re-raises it with the generic message. | Fixed on 2026-10-06 by upgrading `opentelemetry-api`, `-sdk`, `-proto` and the OTLP exporters to 1.45.0 (`fastapi 0.142.2` needs `opentelemetry-api>=1.44.0`, and `opentelemetry-sdk` pins the API exactly). `rag-service/requirements.txt` now pins all five to 1.45.0 ([ADR-021](decisions.md#adr-021-pin-rag-service-dependencies-and-the-opentelemetry-family)). If it comes back, run `../.venv/bin/python -m pip check` and reinstall from `requirements.txt`. |
| rag-service `/health` shows `"status":"error"` with a `detail` | Startup failed. The `detail` says why, for example the PDF path or a failed model pull. | Read `detail`. If the PDF is not found, start rag-service from `rag-service/`. If the pull failed, check Ollama and `ollama list`. (Code path only; not triggered on purpose during this review.) |
| rag-service `/ask` returns `503` with `index unavailable: ...` | Same cause as `error` above, seen through `/ask`. | Same as above. |
| Backend `404` on the messages endpoint, and the page forgets the conversation | The stored id is unknown to the backend, for example after deleting `backend/data/`. | The page clears the id and starts a new conversation on the next send. Nothing to do. |
| Backend `400` "Malformed request body" or "Invalid request" | Bad JSON, a blank message, or a message over 2000 characters after trimming. | Fix the request. |
| Backend `415` or `405` | Wrong `Content-Type` or method. | Send `Content-Type: application/json` with `POST`. |
| Page shows errors on every send | Backend not on `:8080`. The Vite proxy forwards `/api` there. | Start the backend, then retry. |
| `scripts/smoke.sh` prints `FAIL` | The line says which check failed and prints up to 500 bytes of the body. HTTP `000` means the URL was unreachable. | Fix the failing layer using the rows above, then rerun. A slow answer can exceed `CHAT_TIMEOUT`; raise it if the model is slow. |
| Port already in use (`8000`, `8080`, `5173`) | An earlier process is still running. | Find it with `lsof -i :8000` (or the port) and stop it. |
| rag-service "PDF not found" or a wrong PDF | The default path `./data/HOA.pdf` is relative to the working directory. | Start from `rag-service/`, or set `RAG_DOC_PATH`. |
| A new `data/` folder appears at the repo root | The backend was started from the root, so H2 wrote `./data/chatdb` there. Only `backend/data/` is gitignored. | Delete the root `data/` folder and start the backend from `backend/`. |

## Known gaps

Found while writing this run book and rechecked against the repo at `be3570f` on 2026-10-06. Gaps 1–9 and 12–14 are fixed; 10 and 11 are resolved or accepted; 15–19 are open. Owners are listed so the right agent or session can fix each one. Contract changes go through the main session.

| # | Gap | Evidence | Owner |
|---|---|---|---|
| 1 | The index is built inside the FastAPI lifespan, which uvicorn runs before it binds the port. So `/health` cannot report `"loading"` while the index builds, and clients see connection refused. The `503` from `/ask` for a loading index is therefore not reachable over HTTP at startup. The backend still returns `503` for that case (connection refused maps to `RagUnavailableException`). | `rag-service/app.py` (`await run_in_threadpool(rag.build_index)` in `lifespan`). In the installed uvicorn, `Server.startup()` calls `await self.lifespan.startup()` before `loop.create_server`. Read from source, and confirmed live on 2026-10-06: `/health` refused the connection 3 seconds after start, while the build was still running. | rag-service-dev. The contract text for `/health` and `503` also assumes a loading state the server cannot show; a contract change goes through the main session. **Fixed 2026-10-06 in `864d09c`.** |
| 2 | The `app.py` module docstring says the index is built "in a background thread", and the rag-service README says `/health` reports progress. Both differ from the code in gap 1. | `rag-service/app.py` lines 3-5; `rag-service/README.md` "Run" section. | rag-service-dev **Fixed 2026-10-06 in `864d09c`** (the code now matches the docstring and README). |
| 3 | `rag-service/requirements.txt` lists `pdfplumber` twice. Several listed packages are not imported by any file under `rag-service/`: `pdfplumber`, `elevenlabs`, `sentence-transformers`, `fastembed`, `unstructured`, `requests`. Whether they are needed as transitive dependencies was not checked. | `grep` of `import` lines in `rag-service/*.py` and `tests/*.py`. | rag-service-dev **Fixed 2026-10-06 in `be52f79`:** unused packages removed, the rest pinned; verified with a fresh venv. |
| 4 | The backend README says the `503` and `504` bodies carry `conversationId`, but it does not say `500` does too. The contract and code do. | `backend/README.md`, "Endpoints" section; `GlobalExceptionHandler` maps `FAILED` with the id. | backend-dev **Fixed 2026-10-06 in `d38b5dc`.** |
| 5 | Root `CLAUDE.md` still lists rag-service, backend and frontend as "pending". Its rag-service section describes the old `run()` pipeline and says the test file loads by `importlib`; the tests now use a `sys.path` insert. | `CLAUDE.md` status table and "rag-service/pdf_rag.py architecture" section; `rag-service/tests/test_pdf_rag.py` top lines. | Main session **Fixed 2026-10-06 in `d38b5dc`.** |
| 6 | `docs/implementation-plan.md` status table still shows phases 1A, 1B and 1C as "Not started". | Status table at the top of the file. | Main session **Fixed 2026-10-06 in `d38b5dc`.** |
| 7 | Root `README.md` still describes the single script and lists packages from before the refactor. It is due to be rewritten in Phase 2. | `README.md`. | integration-tester (Phase 2) **Fixed 2026-10-06 in `d38b5dc`.** |
| 8 | `scripts/` and `scripts/smoke.sh` do not exist yet. Phase 2 is not started. | `ls` of the repo root. | integration-tester (Phase 2) **Fixed 2026-10-06 in `be3570f`:** `scripts/smoke.sh` added and passing against the live stack. |
| 9 | Six git worktrees exist under `.claude/worktrees/` (one locked: `agent-acdd7b1c3a15d0f2f`). Their `worktree-agent-*` branches and `feature/rag-service-phase1a` remain after merge. | `git worktree list`. | Main session **Fixed 2026-10-06:** all six worktrees removed and seven local branches deleted; `git worktree list` shows only the main checkout. SHAs for restoring them are in the [CHANGELOG](../CHANGELOG.md#branches-deleted-on-2026-10-06). The empty `.claude/worktrees/` folder was removed too. |
| 10 | Two commits on worktree branches (`51edb5f`, `ae0dff0`) are superseded attempts at Phases 1B and 1C and are not on this branch. | `git merge-base --is-ancestor`. See [CHANGELOG](../CHANGELOG.md#not-on-this-branch). | Main session **Resolved 2026-10-06:** their branches were deleted. The commits are unreachable and will go at the next `git gc`; restore with `git branch <name> <sha>` before then if needed. |
| 11 | `starter-1.py` and `start-2.py` at the repo root are untracked, although the plan says they stay at the root. `docs/agents.md` has uncommitted edits. | `git status`. | Main session. **Changed 2026-10-06:** the `docs/agents.md` edits were committed in `d38b5dc`. The two starter scripts are still untracked, by the user's choice; `CLAUDE.md` and the root README describe them as standalone demos. |
| 12 | The shared `.venv` cannot import `chromadb` (an OpenTelemetry version mismatch, seen 2026-10-06). rag-service therefore starts with `status: error` in this environment, and the `/ask` answer path cannot be run here. The unit tests pass because they mock the pipeline steps and never reach this import. | Live run of `uvicorn app:app` from `rag-service/` on 2026-10-06. | Main session (venv); rag-service-dev if the requirements need a pin **Fixed 2026-10-06** in the venv with pip (no commit); pinned in `be52f79`. |
| 13 | Phase 2 has not run here. There is no recorded full-stack run and no smoke test. The answer path through a real Ollama model is not verified in these docs. | Plan status table; no `scripts/`. | integration-tester (Phase 2) **Fixed 2026-10-06 in `be3570f`:** `scripts/smoke.sh` added and passing against the live stack. |
| 14 | Answers through `gemma4` took 40–84 seconds and sometimes came back empty (200 with `"answer": ""`, shown as a blank bubble). The model's default hidden reasoning used most of the tokens and sometimes all of them (`done_reason=length`). | Phase 2 browser check; probe of the raw `ChatOllama` message on 2026-10-06. | **Fixed 2026-10-06 in `2063d33`:** `RAGConfig.reasoning=False`; `/ask` returns 500 on a blank answer. Answers now take 8–22 seconds. |
| 15 | Contract B's `500` row says only "the chain failed (e.g. Ollama down)". Since `2063d33`, rag-service also returns `500` for a blank answer. The `503` row lists only `{"detail": "index loading"}`; when the build failed, `/ask` returns `503` with `{"detail": "index unavailable: ..."}` (since `d065033`). | `docs/api-contract.md` Contract B; `rag-service/app.py` `ask()`. | Main session (contract change). The backend already maps both to its own 500 and 503, so no module change is needed. |
| 16 | The shared `.venv` still has the packages that `be52f79` removed from `requirements.txt` (for example `pdfplumber`, `unstructured`, `fastembed`, `sentence-transformers`, `elevenlabs`). `pip check` is clean and `import chromadb` works, so this is harmless, but the shared venv is not the same as a fresh install from `requirements.txt`. | `.venv/bin/python -m pip list`, 2026-10-06. | Main session (venv) |
| 17 | `docs/implementation-plan.md` section 10 records "Unit suites on the merged tree: rag-service 23". That was true when the suites ran, but `2063d33` added two tests, so the current count is 25. The line above the status table still says 22 (true for Phase 1). | `docs/implementation-plan.md` lines 19 and 214; the unit run on 2026-10-06 at `be3570f`. | Main session **Fixed 2026-10-06** in the Phase 2 completion commit. |
| 18 | Timing figures differ between files. Answers: "8–22 seconds" (`2063d33` message, implementation plan) and "about 8–25 seconds" (root `README.md`). Smoke test: "10–30 seconds" (`README.md`, `CLAUDE.md`) and "can take up to a minute" (`scripts/smoke.sh` header; its timeout is 180 s). None is wrong as an estimate. | The files named. | Main session (`README.md`, `CLAUDE.md`), integration-tester (`scripts/smoke.sh`) **Fixed 2026-10-06** in the Phase 2 completion commit. |
| 19 | Only rag-service guards against a blank answer. The backend's `RagClient` treats a missing or `null` answer as a failure (500) but passes a blank string through, and the frontend renders `reply.answer` as is. Since `2063d33` rag-service never returns a blank 200, so this is a note, not a bug. | `backend/.../rag/RagClient.java` (`body.answer() == null`); `frontend/src/App.jsx` (`content: reply.answer`). | backend-dev, frontend-dev (no action needed unless rag-service changes) |
