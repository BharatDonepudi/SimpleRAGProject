# PDF Chat

A browser chat that answers questions about a PDF (`rag-service/data/HOA.pdf`) using a local LLM through [Ollama](https://ollama.com). No hosted LLM APIs are involved.

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    └── H2 file DB (transcript log)
```

- **rag-service**: Python FastAPI around a LangChain RAG pipeline. It builds an in-memory Chroma index of the PDF once at startup, then answers each question with `MultiQueryRetriever` and the `gemma4` chat model.
- **backend**: Spring Boot REST API. It logs every conversation to an H2 file database and forwards questions to rag-service.
- **frontend**: React chat page. It shows a "Thinking…" state, then the full answer (no streaming).

Conversation history is stored as a transcript log only. The RAG chain answers each question on its own, without earlier turns.

## Layout

| Folder | What | Details |
|---|---|---|
| `rag-service/` | FastAPI service, RAG pipeline, prompts, tests | [rag-service/README.md](rag-service/README.md) |
| `backend/` | Spring Boot 4.1 (Maven wrapper, Java 21) | [backend/README.md](backend/README.md) |
| `frontend/` | React + Vite, plain JavaScript | [frontend/README.md](frontend/README.md) |
| `docs/` | API contract, implementation plan, agents guide, knowledge base, changelog | [docs/api-contract.md](docs/api-contract.md) is the source of truth for every HTTP boundary |

`starter-1.py` and `start-2.py` at the root are standalone Ollama demos and aren't part of the app.

## Prerequisites

- Python 3.13 with a virtualenv at `.venv/` (shared by all Python code)
- JDK 21 (Maven isn't needed; the backend has `./mvnw`)
- Node.js 20 or newer
- Ollama running locally, with the models pulled:

  ```bash
  ollama pull gemma4
  ollama pull nomic-embed-text
  ```

## Run the full stack

Start each part in its own terminal, in this order:

```bash
# 1. rag-service (from rag-service/, because the PDF path is relative to it)
cd rag-service
../.venv/bin/pip install -r requirements.txt   # first time only
../.venv/bin/uvicorn app:app --port 8000
curl -s localhost:8000/health                  # wait for "status": "ok"

# 2. backend
cd backend
./mvnw spring-boot:run

# 3. frontend
cd frontend
npm install                                    # first time only
npm run dev
```

Open http://localhost:5173 and ask a question. Answers from the local model take about 8–22 seconds.

With the stack running, check it end to end from the repo root:

```bash
scripts/smoke.sh    # exits non-zero on the first failed check
```

It checks rag-service and backend health, asks one real question (so it takes 10–30 seconds), reads back the transcript, and checks the 400 and 404 error shapes. `RAG_URL`, `BACKEND_URL`, `QUESTION` and `CHAT_TIMEOUT` override its defaults.

## Test

Each module's unit tests run without Ollama, the network or the other modules:

```bash
cd rag-service && ../.venv/bin/python -m unittest discover -s tests -v
cd backend && ./mvnw test
cd frontend && npm test -- --run
```

## Status

Phases 0, 1 and 2 are done: the full stack runs, `scripts/smoke.sh` passes, the browser chat answers questions, and rag-service going down returns 503 without losing the conversation. See [docs/implementation-plan.md](docs/implementation-plan.md) for the plan and [docs/CHANGELOG.md](docs/CHANGELOG.md) for what changed in each merge.
