# Architecture

Four processes and one local model server. The HTTP shapes live in [docs/api-contract.md](../api-contract.md); this page covers how the pieces fit, what runs where, and what is stored where. It does not repeat the contract.

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    │
                                    └── H2 file DB (transcript log)
```

## Processes

| Process | Port | Language and stack | Folder | Role |
|---|---|---|---|---|
| Vite dev server | 5173 | React 19, Vite 8 (plain JavaScript) | `frontend/` | Serves the chat page. Proxies `/api` to the backend, so the browser never needs CORS. |
| Backend | 8080 | Spring Boot 4.1.1, Java 21, Maven wrapper | `backend/` | Public REST API (`/api/chat`, `/api/conversations/{id}/messages`). Saves the transcript and calls rag-service. |
| rag-service | 8000 | FastAPI on LangChain, Python (shared `.venv`) | `rag-service/` | Builds the PDF index once at startup and answers questions. Only the backend calls it. |
| Ollama | 11434 | Local model server (not in this repo) | n/a | Serves the chat model `gemma4` and the embedding model `nomic-embed-text`. |

The browser talks only to the Vite dev server (or whatever serves `/api` in production). The backend is the only caller of rag-service. Ollama is called only by rag-service.

## How they talk

| Link | Protocol | Contract |
|---|---|---|
| Browser → backend | JSON over HTTP, relative `/api` URLs | [Contract A](../api-contract.md#contract-a--frontend--backend-spring-boot-port-8080) |
| Backend → rag-service | JSON over HTTP, `POST /ask` and `GET /health` | [Contract B](../api-contract.md#contract-b--backend--rag-service-fastapi-port-8000) |
| rag-service → Ollama | Ollama client (`ollama`, `langchain-ollama`) | Not in the contract; configured by `RAG_MODEL` and `RAG_EMBED_MODEL` |

Timeouts follow the call chain. Ollama answers take 10 to 60 seconds on a local model, so the backend's read timeout to rag-service is 120 seconds (`rag.service.read-timeout`). The connect timeout is fixed at 5 seconds. See [run-book](run-book.md#common-failures) for what each timeout looks like to the user.

## Data flow for one question

1. The user types a question. The frontend calls `POST /api/chat` with `conversationId` (null for a new conversation) and the message.
2. `ChatService` resolves the conversation. A new one is inserted into the H2 `conversations` table.
3. `ChatService` saves the user message to `messages` **before** calling rag-service. The message stays even if the next step fails.
4. `RagClient` sends `POST /ask` with `{ "question": ... }` to rag-service.
5. rag-service runs the chain in `pdf_rag.py`:
   - `MultiQueryRetriever` asks `gemma4` to rewrite the question into five variants.
   - Each variant is embedded with `nomic-embed-text` and searched in the in-memory Chroma store (chunk size 1200, overlap 300).
   - The retrieved chunks, the question and the guardrail prompt in `prompts.py` go to `gemma4`. `StrOutputParser` returns the text.
6. rag-service returns `{ "answer": ... }`. The backend saves it as an `assistant` message and returns `{ conversationId, answer, createdAt }`.
7. The frontend stores `conversationId` in `sessionStorage` and shows the answer.

If anything fails, the backend maps it to 503, 504 or 500 and includes `conversationId` in the body (see [ADR-011](decisions.md#adr-011-conversationid-on-503-504-and-500)).

Conversation history is not sent to rag-service. Each question is answered on its own. See [ADR-003](decisions.md#adr-003-transcript-only-memory-rag-chain-unchanged).

## What is stored where

| Data | Where | Lifetime | Notes |
|---|---|---|---|
| Conversations and messages (transcript) | H2 file database `backend/data/chatdb.mv.db` | Survives restarts. Deleted only if the folder is deleted. | Tables `conversations` and `messages`. Created by Hibernate (`ddl-auto=update`). `backend/data/` is gitignored. |
| Vector index (document chunks and embeddings) | Chroma, in memory inside rag-service | Rebuilt from `rag-service/data/HOA.pdf` on every rag-service start. Nothing is written to disk. | Startup is slow because the PDF is embedded again each time. Persisting Chroma is listed as later work in the [plan](../implementation-plan.md#15-later-out-of-scope-for-v1). |
| Model weights | Ollama's own store | Until removed with Ollama | Pulled with `ollama pull gemma4` and `ollama pull nomic-embed-text`. |
| Current conversation id | Browser `sessionStorage`, key `chat.conversationId` | Lasts for one browser tab | A new tab starts a new conversation. Read and write are guarded by try/catch. |
| Source document | `rag-service/data/HOA.pdf` | In git | Path is relative to the rag-service working directory (`RAG_DOC_PATH`). |

## Where to look in code

- Backend request path: `backend/src/main/java/com/llmusingapi/backend/chat/ChatController.java` → `ChatService.java` → `rag/RagClient.java`.
- rag-service request path: `rag-service/app.py` (`/ask`) → `PDFRAGApp.answer()` in `rag-service/pdf_rag.py`.
- Frontend request path: `frontend/src/App.jsx` → `frontend/src/api/chatClient.js`.

Module details are in [modules/](modules/).
