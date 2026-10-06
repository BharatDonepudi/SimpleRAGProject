# Frontend

React + Vite chat page for the HOA document assistant. Plain JavaScript. It calls the Spring Boot backend through relative `/api` URLs (see `docs/api-contract.md`, Contract A).

## Prerequisites

- Node.js 20 or newer (Node 24 is what this project uses). Check with:

  ```bash
  node -v
  ```

- The backend must be running on **port 8080**. In development Vite proxies `/api` to `http://localhost:8080`, so the page will show errors if nothing is listening there.

## Setup

```bash
cd frontend
npm install
```

## Run the dev server

```bash
npm run dev
```

Open http://localhost:5173.

## Tests

Tests use Vitest and React Testing Library. They mock `fetch` and never call the real backend, so they run without Ollama or Spring Boot.

```bash
npm test              # watch mode
npm test -- --run     # one pass, then exit
```

Run a single test file:

```bash
npx vitest run src/App.test.jsx
```

## Build

```bash
npm run build
```

The output goes to `dist/`. To preview it locally, run `npm run preview`. Note that `preview` also needs the backend on port 8080 for `/api` requests.

## Layout

| Path | What |
|---|---|
| `src/App.jsx` | Page state: messages, conversation id, pending, error. Restores history on load. |
| `src/ChatWindow.jsx` | Scrollable bubbles, "Thinking…" while waiting, error bubble. |
| `src/MessageInput.jsx` | Textarea and Send. Enter sends, Shift+Enter adds a newline. |
| `src/api/chatClient.js` | `sendMessage` (POST `/api/chat`) and `getMessages` (GET `/api/conversations/{id}/messages`). |
| `src/storage.js` | Reads and writes the conversation id in `sessionStorage`. |
| `src/index.css` | All styles. Light and dark mode, works at phone width. |
| `vite.config.js` | Dev proxy for `/api` and the Vitest jsdom setup. |
