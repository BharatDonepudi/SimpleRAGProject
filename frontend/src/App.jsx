import { useEffect, useState } from 'react'
import ChatWindow from './ChatWindow.jsx'
import MessageInput from './MessageInput.jsx'
import { getMessages, sendMessage } from './api/chatClient.js'
import {
  clearConversationId,
  loadConversationId,
  saveConversationId,
} from './storage.js'

let keySeq = 0
const newKey = () => `m${++keySeq}`

// Transcript rows from GET /api/conversations/{id}/messages become bubbles.
const toBubble = (m) => ({ key: newKey(), role: m.role, content: m.content })

export default function App() {
  const [messages, setMessages] = useState([])
  const [conversationId, setConversationId] = useState(null)
  const [pending, setPending] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [error, setError] = useState(null)

  // On load, restore the transcript of the conversation saved for this tab.
  useEffect(() => {
    const saved = loadConversationId()
    if (!saved) return

    let cancelled = false
    setConversationId(saved)
    setRestoring(true)
    getMessages(saved)
      .then((transcript) => {
        if (!cancelled) setMessages(transcript.map(toBubble))
      })
      .catch((err) => {
        if (cancelled) return
        if (err.status === 404) {
          // Unknown conversation: forget it and start fresh.
          clearConversationId()
          setConversationId(null)
        } else {
          setError(err.message)
        }
      })
      .finally(() => {
        if (!cancelled) setRestoring(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  function rememberConversation(id) {
    setConversationId(id)
    saveConversationId(id)
  }

  async function handleSend(text) {
    const message = text.trim()
    if (!message || pending || restoring) return

    setError(null)
    setMessages((prev) => [...prev, { key: newKey(), role: 'user', content: message }])
    setPending(true)
    try {
      const reply = await sendMessage(conversationId, message)
      rememberConversation(reply.conversationId)
      setMessages((prev) => [
        ...prev,
        { key: newKey(), role: 'assistant', content: reply.answer },
      ])
    } catch (err) {
      if (err.status === 404) {
        // The stored conversation no longer exists: the next message starts a new one.
        clearConversationId()
        setConversationId(null)
      } else if (err.conversationId) {
        // 503/504: the backend saved the user message and kept the conversation.
        rememberConversation(err.conversationId)
      }
      setError(err.message)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>HOA Document Chat</h1>
      </header>
      <ChatWindow messages={messages} pending={pending} error={error} />
      <MessageInput onSend={handleSend} disabled={pending || restoring} />
    </div>
  )
}
