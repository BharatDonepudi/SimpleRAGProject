# API Contract

The single source of truth for every HTTP boundary in this repo. The frontend, backend and rag-service are built in parallel against this file, so **no module may change it on its own**. A change goes through the main session: edit this file, commit, then tell the affected agents.

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    │
                                    └── H2 file DB (transcript log)
```

All bodies are JSON (`Content-Type: application/json`, UTF-8). Timestamps are ISO-8601 UTC strings, e.g. `"2026-10-05T22:15:03.120Z"`. IDs are UUID strings.

---

## Contract A — Frontend → Backend (Spring Boot, port 8080)

In development the frontend calls relative `/api/...` URLs and the Vite dev server proxies them to `http://localhost:8080`, so no CORS setup is needed.

### `POST /api/chat`

Ask a question. Omit or null `conversationId` to start a new conversation.

Request:
```json
{ "conversationId": null, "message": "What are the main points that I should refer to first" }
```

| Field | Type | Rules |
|---|---|---|
| `conversationId` | string (UUID) or null | Optional. If given, it must exist. |
| `message` | string | Required. 1–2000 characters after trimming. |

`200 OK`:
```json
{
  "conversationId": "3f6c2a8e-1b1d-4c7e-9a52-0d8b8e6f1a10",
  "answer": "The main points are ...",
  "createdAt": "2026-10-05T22:15:03.120Z"
}
```
`createdAt` is the timestamp of the assistant message.

Errors (all errors use this shape):
```json
{ "error": "human-readable message" }
```

On `503`, `504` and `500` the body also carries `conversationId` once the conversation exists (including one created by this request), so the client can keep the transcript:
```json
{ "error": "The document assistant is not ready yet. Try again shortly.", "conversationId": "3f6c2a8e-1b1d-4c7e-9a52-0d8b8e6f1a10" }
```
`conversationId` is omitted when no conversation was created (for example, a 400 or a 404).

| Status | When |
|---|---|
| `400` | `message` is missing, blank, or over 2000 characters; malformed JSON; malformed `conversationId` |
| `404` | `conversationId` given but unknown |
| `405` | wrong HTTP method on a known path |
| `415` | request body is not `Content-Type: application/json` |
| `503` | rag-service is unreachable or still building its index |
| `504` | rag-service did not answer within the backend's read timeout (default 120s) |
| `500` | rag-service returned an error (its 500 or 422), or anything else unexpected. Never leaks stack traces or rag-service details |

The user message is saved before rag-service is called, so it stays in the transcript even if the call fails with 503, 504 or 500. The client should store the returned `conversationId` on any of these and keep using it.

### `GET /api/conversations/{id}/messages`

The full transcript of a conversation, oldest first.

`200 OK`:
```json
[
  { "role": "user", "content": "What are the main points ...", "createdAt": "2026-10-05T22:14:41.002Z" },
  { "role": "assistant", "content": "The main points are ...", "createdAt": "2026-10-05T22:15:03.120Z" }
]
```

| Status | When |
|---|---|
| `404` | unknown conversation id (`{ "error": "..." }`) |
| `400` | id is not a valid UUID |

---

## Contract B — Backend → rag-service (FastAPI, port 8000)

Only the backend calls rag-service. The browser never talks to it directly.

### `POST /ask`

Request:
```json
{ "question": "What are the main points that I should refer to first" }
```

`200 OK`:
```json
{ "answer": "The main points are ..." }
```

| Status | Body | When |
|---|---|---|
| `422` | FastAPI default validation body | `question` missing or not a string |
| `503` | `{ "detail": "index loading" }` | the PDF index is still being built at startup |
| `500` | `{ "detail": "..." }` | the chain failed (e.g. Ollama down) |

The call is synchronous and can take 10–60 seconds on a local model. Callers must set a read timeout of at least 120 seconds.

### `GET /health`

`200 OK`:
```json
{ "status": "ok", "chunks": 42 }
```
`status` is `"loading"` (and `chunks` is `0`) until the index is ready, then `"ok"`. If startup failed (e.g. the PDF is missing), `status` is `"error"` and an extra `"detail"` string explains why.

---

## Ports and config keys

| Process | Port | Config |
|---|---|---|
| frontend (Vite dev) | 5173 | proxy target in `frontend/vite.config.js` |
| backend | 8080 | `server.port`, `rag.service.url` (default `http://localhost:8000`), `rag.service.read-timeout` (default `120s`) |
| rag-service | 8000 | `RAG_DOC_PATH`, `RAG_MODEL`, `RAG_EMBED_MODEL` env vars |
| Ollama | 11434 | models `gemma4` and `nomic-embed-text` |
