# Module: backend

Spring Boot chat REST API. It serves the frontend under `/api`, saves every conversation and message in an H2 file database, and calls rag-service for answers. Owner: `backend-dev`. Module README: [backend/README.md](../../../backend/README.md).

## Stack

- Spring Boot 4.1.1 (parent POM), Java 21, Maven wrapper `./mvnw`.
- Starters: web MVC, data JPA, validation, actuator. H2 as the database.
- `spring.jpa.open-in-view=false`.

## Files

| Package | Classes | Role |
|---|---|---|
| root | `BackendApplication` | Spring Boot entry point. |
| `chat/` | `ChatController` | `POST /api/chat`, `GET /api/conversations/{id}/messages`. |
| `chat/` | `ChatService` | Resolves the conversation, saves the user message, calls rag-service, saves the answer, builds the transcript. |
| `chat/` | `ChatRequest`, `ChatResponse`, `MessageResponse`, `ApiTimestamps` | Records. `ChatRequest` trims the message in its compact constructor, then applies `@NotBlank` and `@Size(max = 2000)`. |
| `chat/` | `AnswerUnavailableException` | Carries `conversationId` and a `Reason` (`UNAVAILABLE`, `TIMEOUT`, `FAILED`). |
| `chat/` | `ConversationNotFoundException` | Unknown conversation id. |
| `rag/` | `RagClient` | `POST /ask` on a `RestClient`. Maps errors to the three rag exceptions. |
| `rag/` | `RagConfig`, `RagServiceProperties` | Builds the `RestClient`. Reads `rag.service.url` and `rag.service.read-timeout`. Connect timeout fixed at 5s. |
| `rag/` | `RagUnavailableException`, `RagTimeoutException`, `RagFailedException` | Rag-side failures. |
| `persistence/` | `Conversation`, `Message`, `Role`, two repositories | JPA entities in tables `conversations` and `messages`. `Message.content` is `@Lob`. |
| `web/` | `GlobalExceptionHandler`, `ErrorResponse` | Maps exceptions to the status codes in Contract A. Bodies are `{ "error": "..." }`, plus `conversationId` for 503, 504 and 500. |

## Endpoints

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/chat` | Body `{ "conversationId": uuid or null, "message": string }`. |
| `GET` | `/api/conversations/{id}/messages` | Transcript, oldest first. `400` for a non-UUID id, `404` if unknown. |
| `GET` | `/actuator/health` | Health check. Returned `"status":"UP"` when checked. |
| `GET` | `/h2-console` | H2 console (enabled in `application.properties`). |

## Request flow

`ChatService.chat()`:

1. `resolveConversation`: a new `Conversation` if the id is null, otherwise `findById` or `ConversationNotFoundException`.
2. `answerAndStore`: saves the user `Message` **first**, then calls `RagClient.ask(question)`. Only the question is sent (no history).
3. On success, saves the assistant `Message` and returns `ChatResponse(conversationId, answer, createdAt)`.
4. On `RagUnavailableException` → `AnswerUnavailableException(UNAVAILABLE)` (503). On `RagTimeoutException` → `TIMEOUT` (504). On any other `RuntimeException` (including `RagFailedException`) → `FAILED` (500). All carry the conversation id.

No transaction spans the rag call ([ADR-010](../decisions.md#adr-010-save-the-user-message-before-the-rag-call)).

## Configuration

`src/main/resources/application.properties`:

| Key | Value | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port. |
| `spring.datasource.url` | `jdbc:h2:file:./data/chatdb` | H2 file. Relative to the start directory. Gitignored under `backend/data/`. |
| `spring.jpa.hibernate.ddl-auto` | `update` | Creates and updates tables at startup. |
| `spring.h2.console.enabled` | `true` | H2 console. |
| `rag.service.url` | `http://localhost:8000` | rag-service base URL. |
| `rag.service.read-timeout` | `120s` | Read timeout. Over it → 504. |

## Tests

48 tests, all passing (re-run 2026-10-06, surefire reports):

| Class | Tests | Covers |
|---|---|---|
| `BackendApplicationTests` | 1 | Context loads. |
| `chat/ChatControllerTest` | 19 | Validation (400s), happy path, error mapping (`@WebMvcTest`). |
| `chat/ChatFlowIntegrationTest` | 5 | Full flow through the app with rag-service mocked. |
| `chat/ChatServiceTest` | 12 | Message order; user message kept when rag fails. |
| `persistence/MessageRepositoryTest` | 4 | Persistence (`@DataJpaTest`). |
| `rag/RagClientTest` | 7 | 200, 503, timeouts (`MockRestServiceServer`). |

Run with `cd backend && ./mvnw test`. No rag-service or network is needed.

## Checked against the running app (2026-10-06)

Started with `./mvnw spring-boot:run` using an in-memory H2 URL and no rag-service:

| Request | Result |
|---|---|
| `GET /actuator/health` | `200`, `"status":"UP"` |
| `POST /api/chat` (valid, rag down) | `503`, body has `conversationId` |
| `POST /api/chat` with bad JSON | `400` "Malformed request body." |
| `POST /api/chat` with `text/plain` | `415` |
| `GET /api/chat` | `405` |
| `GET /api/conversations/nope/messages` | `400` "Invalid value for parameter 'id'." |
| `GET /api/conversations/{unknown}/messages` | `404` "Conversation not found." |

The `200` answer path was not run, because it needs rag-service and Ollama.

## Known limitations

- **H2 file path is relative to where you start the app.** Start from `backend/`, or data goes to a new `data/` folder.
- **H2 console on with no auth.** Fine for local development; not for anything shared.
- **No CORS config.** Development relies on the Vite proxy ([ADR-017](../decisions.md#adr-017-vite-dev-proxy-instead-of-cors)).
- **Connect failures are reported as 503, read timeouts as 504.** A rag-service that accepts connections and then hangs gives 504 after 120s.
- **README omits 500's `conversationId`.** See [run-book known gaps](../run-book.md#known-gaps), item 4.
- **Single rag-service URL.** No retries and no circuit breaker.

## Related

- [Architecture](../architecture.md), [run-book](../run-book.md), [decisions](../decisions.md) (ADR-002, ADR-006, ADR-010, ADR-011, ADR-012, ADR-013).
- Commits: `1131c7d` (Phase 1B), `2413a12` (500 carries `conversationId`).
