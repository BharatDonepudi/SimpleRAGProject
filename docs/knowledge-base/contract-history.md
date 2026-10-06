# Contract history

Every change to [docs/api-contract.md](../api-contract.md), oldest first. Built from `git log -p -- docs/api-contract.md`. The contract has no version number, so each commit is the version.

Rule for changes: the main session edits the contract and commits it, then the affected modules are updated. No module changes it alone ([ADR-008](decisions.md#adr-008-contract-first-one-owner-per-module)).

| # | Commit | Date | Contract | What changed | Why (from the commit and the contract text) | Modules updated |
|---|---|---|---|---|---|---|
| 1 | `b8b0294` | 2026-10-05 | A and B | Created the contract: Contract A (`POST /api/chat`, `GET /api/conversations/{id}/messages`), Contract B (`POST /ask`, `GET /health`), ports and config keys. | Modules are built in parallel, so the shapes had to be fixed first. | All three (Phase 1A to 1C) |
| 2 | `06a65c5` | 2026-10-06 | A | Added `conversationId` to `503` and `504` bodies, with the rule that it is omitted when no conversation exists. Added `405` and `415` to the status table. Added `400` for a malformed `conversationId`. Added the sentence "The client should store the returned `conversationId` on 503/504 and keep using it." | The user message is saved before rag-service is called, so the client needs the id to keep the same transcript after a failed answer. `405` and `415` were already returned by the backend and were missing from the table. | Backend (`1131c7d`), frontend (`3a6e764`) |
| 3 | `ce192d9` | 2026-10-06 | A | `conversationId` also on `500`. The `500` row now covers rag-service's `500` or `422`, and anything unexpected, and says that details are never leaked. The sentence about keeping the id now lists 503, 504 and 500. | rag-service errors (`500`, `422`) also happen after the user message is saved, so the same rule applies. The browser still must not see rag-service details. | Backend (`2413a12`), frontend (`121f813`) |

No later commit changes the contract. `acc6607` (the implementation plan) does not touch it, and no commit changes Contract B after `b8b0294`.

**Since `ce192d9`:** checked with `git log -- docs/api-contract.md` at `be3570f` (2026-10-06). The commits `d38b5dc` to `be3570f` (knowledge base, background index build, pinned requirements, reasoning off, smoke test) did not change the contract. `864d09c` changed rag-service to match it (see below), and `2063d33` added a case the contract does not list.

## Current state after `ce192d9`

- **Contract A `503`, `504`, `500`:** body is `{ "error": "...", "conversationId": "..." }` once the conversation exists.
- **Contract A `500`:** covers rag-service errors and unexpected backend errors.
- **Contract B:** unchanged since `b8b0294`.

## Mismatches with the code

- **Fixed in `864d09c`:** Contract B says `/health` reports `"loading"` while the index is built. Until `864d09c`, uvicorn did not accept connections until the build finished, so that state was not visible over HTTP. The code was changed, not the contract ([ADR-020](decisions.md#adr-020-build-the-index-in-a-background-thread)).
- **Open:** Contract B's `500` row says "the chain failed (e.g. Ollama down)". Since `2063d33`, rag-service also returns `500` `{"detail": "answer generation failed"}` for a blank answer ([ADR-022](decisions.md#adr-022-model-reasoning-off-and-a-blank-answer-is-a-500)).
- **Open:** Contract B's `503` row shows only `{"detail": "index loading"}`. When the index build failed, `/ask` returns `503` with `{"detail": "index unavailable: <reason>"}` (since `d065033`).

Both open items are text gaps only: the backend maps any rag-service `500` to its own `500` and any `503` to `503`. A contract edit is the main session's call. See [run-book known gaps](run-book.md#known-gaps), item 15.

## Verified against code

- Backend status mapping for `400`, `404`, `405`, `415`, `503` and the `conversationId` field was checked against the running backend on 2026-10-06 (no rag-service running, so `503` came from a connection failure).
- Contract B `503` detail text (`"index loading"`) and `500` (`"answer generation failed"`) match `rag-service/app.py` at `be3570f`.
- The Contract A shapes for `200`, `400` and `404` and the transcript endpoint are checked live by `scripts/smoke.sh` (`be3570f`); the `503` path was checked by hand in Phase 2.
