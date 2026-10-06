// Client for Contract A (docs/api-contract.md). All URLs are relative so the
// Vite dev proxy (or any same-origin reverse proxy) forwards them to the backend.

const API = '/api'

/**
 * Turns a non-2xx response into an Error. The message is the server's `error`
 * field, falling back to the HTTP status text. `status` and, on 503/504,
 * `conversationId` are attached so callers can react to them.
 */
async function toError(res) {
  let body = null
  try {
    body = await res.json()
  } catch {
    body = null
  }
  const serverMessage = body && typeof body.error === 'string' ? body.error : ''
  const fallback = res.statusText || `Request failed with status ${res.status}`
  const error = new Error(serverMessage || fallback)
  error.status = res.status
  error.conversationId =
    body && typeof body.conversationId === 'string' ? body.conversationId : null
  return error
}

/**
 * POST /api/chat. Pass null as conversationId to start a new conversation.
 * Resolves with { conversationId, answer, createdAt }.
 */
export async function sendMessage(conversationId, message) {
  const res = await fetch(`${API}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId: conversationId ?? null, message }),
  })
  if (!res.ok) throw await toError(res)
  return res.json()
}

/**
 * GET /api/conversations/{id}/messages. Resolves with the transcript,
 * oldest first: [{ role, content, createdAt }].
 */
export async function getMessages(conversationId) {
  const res = await fetch(
    `${API}/conversations/${encodeURIComponent(conversationId)}/messages`,
    { method: 'GET', headers: { Accept: 'application/json' } },
  )
  if (!res.ok) throw await toError(res)
  return res.json()
}
