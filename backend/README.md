# backend

Spring Boot chat REST API. It stores conversations in an H2 file database, forwards each question to rag-service (Contract B) and serves the answer to the frontend (Contract A). The full API is in [`docs/api-contract.md`](../docs/api-contract.md).

```
React (Vite, :5173) ──/api──▶ Spring Boot (:8080) ──HTTP──▶ rag-service FastAPI (:8000) ──▶ Ollama (:11434)
                                    └── H2 file DB (transcript log)
```

## Prerequisites

- JDK 21

  ```bash
  java -version   # should report version 21
  ```

- No Maven install is needed. Use the bundled `./mvnw` wrapper (Maven 3.9).

## Run

```bash
cd backend
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. Questions only get answers when rag-service is also running on `http://localhost:8000`. Without it, `POST /api/chat` returns `503` and the user message is still saved.

## Test

Tests do not need rag-service, Ollama or the network. rag-service is mocked.

```bash
cd backend
./mvnw test                          # all tests
./mvnw test -Dtest=ChatServiceTest   # a single test class
```

## H2 console

With the app running, open <http://localhost:8080/h2-console> and use these settings:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/chatdb` |
| User Name | `sa` |
| Password | (empty) |

The database file is `backend/data/chatdb.mv.db`, created relative to the directory you start the app from. Tables: `conversations` and `messages`.

## Configuration

Set in `src/main/resources/application.properties`. Each value can be overridden with the usual Spring mechanisms, for example `--rag.service.url=http://other-host:8000`.

| Key | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port of the backend |
| `spring.datasource.url` | `jdbc:h2:file:./data/chatdb` | H2 file database for the transcript log |
| `spring.jpa.hibernate.ddl-auto` | `update` | Creates and updates the tables at startup |
| `spring.h2.console.enabled` | `true` | Enables the H2 console at `/h2-console` |
| `rag.service.url` | `http://localhost:8000` | Base URL of rag-service |
| `rag.service.read-timeout` | `120s` | How long to wait for an answer. Requests that exceed it return `504` |

The connect timeout to rag-service is fixed at 5 seconds.

## Endpoints

| Method and path | Purpose |
|---|---|
| `POST /api/chat` | Ask a question. Body: `{"conversationId": null or UUID, "message": "..."}` |
| `GET /api/conversations/{id}/messages` | Transcript of a conversation, oldest first |

Errors use the body `{"error": "..."}`. On `503`, `504` and `500` the body also includes `conversationId` once the conversation exists (including one this request created), so the client can keep the conversation. The user message is saved before rag-service is called, so it stays in the transcript on any of these. A `500` never includes stack traces or rag-service details. Status codes: `400`, `404`, `405`, `415`, `503`, `504`, `500`.

## Package layout

```
src/main/java/com/llmusingapi/backend/
  chat/         ChatController, ChatService, request/response records
  rag/          RagClient (RestClient), RagConfig, RagServiceProperties, rag exceptions
  persistence/  Conversation, Message, Role, Spring Data repositories
  web/          GlobalExceptionHandler, ErrorResponse
```
