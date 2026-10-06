import { useState } from 'react'

export default function MessageInput({ onSend, disabled }) {
  const [text, setText] = useState('')

  function submit() {
    const message = text.trim()
    if (!message || disabled) return
    onSend(message)
    setText('')
  }

  function handleKeyDown(e) {
    // Enter sends; Shift+Enter inserts a newline. Ignore Enter while an IME is composing.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <form
      className="message-input"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <textarea
        aria-label="Message"
        placeholder="Ask a question…"
        rows={2}
        value={text}
        disabled={disabled}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
      />
      <button type="submit" disabled={disabled}>
        Send
      </button>
    </form>
  )
}
