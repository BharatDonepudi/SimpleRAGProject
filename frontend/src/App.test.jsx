import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.jsx'

const CONV_KEY = 'chat.conversationId'

// Minimal stand-in for a fetch Response; the client only uses ok, status, statusText and json().
function jsonResponse(status, body, statusText = '') {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    json: async () => body,
  }
}

// A promise the test resolves by hand, so "pending" can be observed.
function deferred() {
  let resolve
  const promise = new Promise((r) => {
    resolve = r
  })
  return { promise, resolve }
}

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  window.sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.sessionStorage.clear()
})

function sentBody(callIndex = 0) {
  return JSON.parse(fetchMock.mock.calls[callIndex][1].body)
}

describe('App', () => {
  it('renders an empty chat and makes no request when nothing is stored', () => {
    render(<App />)

    expect(screen.getByText(/ask a question/i)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('sends a message: user bubble, then Thinking…, then the answer', async () => {
    const user = userEvent.setup()
    const reply = deferred()
    fetchMock.mockReturnValueOnce(reply.promise)
    render(<App />)

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'What is the HOA fee?{Enter}')

    expect(screen.getByText('What is the HOA fee?')).toHaveClass('user')
    expect(screen.getByText('Thinking…')).toBeInTheDocument()

    reply.resolve(
      jsonResponse(200, {
        conversationId: 'conv-1',
        answer: 'It is $120 per month.',
        createdAt: '2026-10-05T22:15:03.120Z',
      }),
    )

    expect(await screen.findByText('It is $120 per month.')).toHaveClass('assistant')
    expect(screen.queryByText('Thinking…')).not.toBeInTheDocument()
    expect(window.sessionStorage.getItem(CONV_KEY)).toBe('conv-1')
  })

  it('disables the input and Send button while a request is pending', async () => {
    const user = userEvent.setup()
    const reply = deferred()
    fetchMock.mockReturnValueOnce(reply.promise)
    render(<App />)

    const box = screen.getByRole('textbox', { name: 'Message' })
    const send = screen.getByRole('button', { name: 'Send' })
    await user.type(box, 'hello{Enter}')

    expect(box).toBeDisabled()
    expect(send).toBeDisabled()

    reply.resolve(jsonResponse(200, { conversationId: 'c', answer: 'hi', createdAt: 'x' }))
    await waitFor(() => expect(box).toBeEnabled())
    expect(send).toBeEnabled()
  })

  it('shows a 503 error bubble and keeps the conversationId the server returned', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        503,
        {
          error: 'The document assistant is not ready yet. Try again shortly.',
          conversationId: 'conv-503',
        },
        'Service Unavailable',
      ),
    )
    render(<App />)

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'first{Enter}')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The document assistant is not ready yet. Try again shortly.')
    expect(screen.getByText('first')).toHaveClass('user')
    expect(window.sessionStorage.getItem(CONV_KEY)).toBe('conv-503')

    // The next message continues that same conversation.
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'conv-503', answer: 'Now it works.', createdAt: 'x' }),
    )
    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'retry{Enter}')
    expect(await screen.findByText('Now it works.')).toBeInTheDocument()
    expect(sentBody(1)).toEqual({ conversationId: 'conv-503', message: 'retry' })
  })

  it('shows a 500 error bubble, stores the returned conversationId, and uses it for the next send', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        500,
        { error: 'The document assistant failed to answer.', conversationId: 'conv-500' },
        'Internal Server Error',
      ),
    )
    render(<App />)

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'broken{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The document assistant failed to answer.',
    )
    expect(screen.getByText('broken')).toHaveClass('user')
    expect(window.sessionStorage.getItem(CONV_KEY)).toBe('conv-500')

    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'conv-500', answer: 'Recovered.', createdAt: 'x' }),
    )
    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'again{Enter}')

    expect(await screen.findByText('Recovered.')).toHaveClass('assistant')
    expect(sentBody(1)).toEqual({ conversationId: 'conv-500', message: 'again' })
  })

  it('a 500 without conversationId leaves the stored id unchanged', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'conv-keep', answer: 'Fine.', createdAt: 'x' }),
    )
    fetchMock.mockResolvedValueOnce(
      jsonResponse(500, { error: 'Something went wrong.' }, 'Internal Server Error'),
    )
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'conv-keep', answer: 'Back.', createdAt: 'x' }),
    )
    render(<App />)

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'one{Enter}')
    expect(await screen.findByText('Fine.')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'two{Enter}')
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong.')
    expect(window.sessionStorage.getItem(CONV_KEY)).toBe('conv-keep')

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'three{Enter}')
    expect(await screen.findByText('Back.')).toBeInTheDocument()
    expect(sentBody(2)).toEqual({ conversationId: 'conv-keep', message: 'three' })
  })

  it('clears the stored conversationId when a send returns 404', async () => {
    const user = userEvent.setup()
    window.sessionStorage.setItem(CONV_KEY, 'gone')
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, [{ role: 'assistant', content: 'old', createdAt: 'x' }]),
    )
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { error: 'Conversation not found' }))
    render(<App />)
    await screen.findByText('old')

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'again{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Conversation not found')
    expect(window.sessionStorage.getItem(CONV_KEY)).toBeNull()
  })

  it('restores the transcript of a stored conversation on load', async () => {
    window.sessionStorage.setItem(CONV_KEY, 'conv-9')
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, [
        { role: 'user', content: 'Earlier question', createdAt: 'a' },
        { role: 'assistant', content: 'Earlier answer', createdAt: 'b' },
      ]),
    )

    render(<App />)

    expect(await screen.findByText('Earlier answer')).toHaveClass('assistant')
    expect(screen.getByText('Earlier question')).toHaveClass('user')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/conversations/conv-9/messages',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('clears a stored conversationId that the backend no longer knows (404 on restore)', async () => {
    window.sessionStorage.setItem(CONV_KEY, 'unknown-id')
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { error: 'Conversation not found' }))

    render(<App />)

    await waitFor(() => expect(window.sessionStorage.getItem(CONV_KEY)).toBeNull())
    expect(screen.getByText(/ask a question/i)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('ignores blank input and sends nothing', async () => {
    const user = userEvent.setup()
    render(<App />)

    const box = screen.getByRole('textbox', { name: 'Message' })
    await user.type(box, '   {Enter}')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByText('Thinking…')).not.toBeInTheDocument()
  })

  it('Shift+Enter inserts a newline instead of sending', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'c', answer: 'ok', createdAt: 'x' }),
    )
    render(<App />)

    const box = screen.getByRole('textbox', { name: 'Message' })
    await user.type(box, 'line one{Shift>}{Enter}{/Shift}line two')

    expect(fetchMock).not.toHaveBeenCalled()
    expect(box).toHaveValue('line one\nline two')
  })

  it('shows a non-404 restore failure as an error and keeps the stored id', async () => {
    window.sessionStorage.setItem(CONV_KEY, 'conv-5')
    fetchMock.mockResolvedValueOnce(
      jsonResponse(504, { error: 'Timed out' }, 'Gateway Timeout'),
    )

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Timed out')
    expect(window.sessionStorage.getItem(CONV_KEY)).toBe('conv-5')
  })

  it('renders the bubbles inside the conversation region', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { conversationId: 'c', answer: 'pong', createdAt: 'x' }),
    )
    render(<App />)

    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'ping{Enter}')

    const region = screen.getByRole('main', { name: 'Conversation' })
    expect(await within(region).findByText('pong')).toBeInTheDocument()
  })
})
