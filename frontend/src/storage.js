// sessionStorage access for the conversation id. Storage can throw (private
// mode, disabled storage, quota), so every call is guarded and fails quietly.

const KEY = 'chat.conversationId'

export function loadConversationId() {
  try {
    return window.sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function saveConversationId(id) {
  try {
    window.sessionStorage.setItem(KEY, id)
  } catch {
    // Storage unavailable: the id only lives in React state for this page load.
  }
}

export function clearConversationId() {
  try {
    window.sessionStorage.removeItem(KEY)
  } catch {
    // Storage unavailable: nothing persisted to clear.
  }
}
