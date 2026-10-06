import { useEffect, useRef } from 'react'

export default function ChatWindow({ messages, pending, error }) {
  const listRef = useRef(null)

  // Keep the newest bubble in view whenever something is added.
  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, pending, error])

  const isEmpty = messages.length === 0 && !pending && !error

  return (
    <main className="chat-window" ref={listRef} aria-label="Conversation">
      {isEmpty && (
        <p className="empty-state">Ask a question about the document to get started.</p>
      )}
      {messages.map((m) => (
        <div key={m.key} className={`bubble ${m.role}`}>
          {m.content}
        </div>
      ))}
      {pending && (
        <div className="bubble assistant pending" aria-live="polite">
          Thinking…
        </div>
      )}
      {error && (
        <div className="bubble error" role="alert">
          {error}
        </div>
      )}
    </main>
  )
}
