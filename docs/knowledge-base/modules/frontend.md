# Module: frontend

React chat page for the HOA document assistant. Plain JavaScript (no TypeScript), Vite 8, React 19. It talks only to the Spring Boot backend, through relative `/api` URLs that the Vite dev server proxies. Owner: `frontend-dev`. Module README: [frontend/README.md](../../../frontend/README.md).

## Files

| File | What it holds |
|---|---|
| `src/main.jsx` | Mounts `App`. |
| `src/App.jsx` | Page state: messages, conversation id, pending, restoring, error. Restores the transcript on load. Sends messages and handles the error statuses. |
| `src/ChatWindow.jsx` | Scrollable bubbles, auto-scroll, a "Thinking" bubble while pending, an error bubble. |
| `src/MessageInput.jsx` | Textarea and Send. Enter sends, Shift+Enter adds a newline. Disabled while pending. Ignores blank input. |
| `src/api/chatClient.js` | `sendMessage(conversationId, message)` (POST `/api/chat`) and `getMessages(conversationId)` (GET `/api/conversations/{id}/messages`). Non-2xx responses become `Error`s with `status`, `conversationId` and the server's `error` text. |
| `src/storage.js` | Reads, writes and clears `chat.conversationId` in `sessionStorage`. Every call is in try/catch. |
| `src/index.css` | All styles, light and dark mode, phone width. |
| `vite.config.js` | Proxies `/api` to `http://localhost:8080`. Vitest config (jsdom, `src/test/setup.js`). |
| `package.json` | Scripts: `dev`, `build`, `test` (vitest), `preview`, `lint` (oxlint). |

## Behavior

- **First load.** If `sessionStorage` has an id, the page sets it and loads the transcript with `getMessages`. A `404` clears the id and starts fresh. Other errors show the error text and keep the id.
- **Send.** Adds the user bubble, sets `pending`, calls `sendMessage`, stores the returned `conversationId`, and adds the answer bubble.
- **Errors.** `404` clears the stored id, so the next send starts a new conversation. `500`, `503` and `504` keep the id when the body has one, because the backend has already saved the user message ([ADR-011](../decisions.md#adr-011-conversationid-on-503-504-and-500)). Other errors show the message and leave the id unchanged.
- **Input is blocked** while a send is pending or the transcript is restoring.

## Tests

19 tests (re-run 2026-10-06, all passing):

| File | Tests | Covers |
|---|---|---|
| `src/App.test.jsx` | 13 | Empty chat; send and answer; input disabled while pending; 503 keeps the returned id; 500 stores the id and uses it on the next send; 500 without an id leaves the stored id; 404 on send clears it; restore on load; 404 on restore clears it; non-404 restore failure keeps it; blank input not sent; Shift+Enter adds a newline. A 504 case is not tested. |
| `src/api/chatClient.test.js` | 6 | Request body for a new and an existing conversation; error text and fallback; transcript GET; 404 error. |

`fetch` is mocked. No backend, Ollama or network is needed.

```bash
cd frontend
npm test -- --run          # one pass
npx vitest run src/App.test.jsx
npm run build              # output in dist/
```

## Checked here (2026-10-06)

- `npm test -- --run`: 2 files, 19 tests passed.
- The running dev server and the proxy to a live backend were not exercised in this review.

## Known limitations

- **Conversation is per tab.** `sessionStorage` is not shared across tabs. A reload in the same tab restores the chat ([ADR-016](../decisions.md#adr-016-conversation-id-in-sessionstorage)).
- **No streaming.** The answer appears all at once ([ADR-004](../decisions.md#adr-004-full-answer-and-a-spinner-no-streaming)).
- **Proxy only in development.** `vite.config.js` proxies `/api`. A production build needs the same origin for `/api` ([ADR-017](../decisions.md#adr-017-vite-dev-proxy-instead-of-cors)). `npm run preview` also needs the backend on `:8080`.
- **A failed restore keeps the id.** A non-404 error on the first load shows the error text and keeps the stored id, so a later send still uses it.
- **Lint script not run here.** `npm run lint` (oxlint) exists; it was not run for this document.

## Related

- [Architecture](../architecture.md), [run-book](../run-book.md), [decisions](../decisions.md) (ADR-004, ADR-007, ADR-011, ADR-016, ADR-017).
- Commits: `3a6e764` (Phase 1C, built on `06a65c5`), `121f813` (keeps the id on 500).
