import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getMessages, sendMessage } from './chatClient.js'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function jsonResponse(status, body, statusText = '') {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    json: async () => body,
  }
}

describe('sendMessage', () => {
  it('POSTs to /api/chat with a JSON body and a null conversationId for a new chat', async () => {
    const reply = { conversationId: 'c1', answer: 'hi', createdAt: 'x' }
    fetchMock.mockResolvedValueOnce(jsonResponse(200, reply))

    const result = await sendMessage(null, 'What is the HOA fee?')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/chat')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(init.body)).toEqual({
      conversationId: null,
      message: 'What is the HOA fee?',
    })
    expect(result).toEqual(reply)
  })

  it('sends an existing conversationId in the body', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { conversationId: 'c1', answer: 'a', createdAt: 'x' }))

    await sendMessage('c1', 'follow up')

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      conversationId: 'c1',
      message: 'follow up',
    })
  })

  it('throws the server error text on a non-2xx response', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(503, { error: 'The document assistant is not ready yet.', conversationId: 'c2' }, 'Service Unavailable'),
    )

    const err = await sendMessage(null, 'hi').catch((e) => e)

    expect(err).toBeInstanceOf(Error)
    expect(err.message).toBe('The document assistant is not ready yet.')
    expect(err.status).toBe(503)
    expect(err.conversationId).toBe('c2')
  })

  it('falls back to the status text when the error body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: async () => {
        throw new SyntaxError('Unexpected token <')
      },
    })

    await expect(sendMessage(null, 'hi')).rejects.toThrow('Bad Gateway')
  })
})

describe('getMessages', () => {
  it('GETs the transcript from /api/conversations/{id}/messages', async () => {
    const transcript = [{ role: 'user', content: 'q', createdAt: 'a' }]
    fetchMock.mockResolvedValueOnce(jsonResponse(200, transcript))

    const result = await getMessages('3f6c2a8e-1b1d-4c7e-9a52-0d8b8e6f1a10')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/conversations/3f6c2a8e-1b1d-4c7e-9a52-0d8b8e6f1a10/messages')
    expect(init.method).toBe('GET')
    expect(init.body).toBeUndefined()
    expect(result).toEqual(transcript)
  })

  it('throws the server error text and status on 404', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { error: 'Conversation not found' }, 'Not Found'))

    const err = await getMessages('missing').catch((e) => e)

    expect(err.message).toBe('Conversation not found')
    expect(err.status).toBe(404)
    expect(err.conversationId).toBeNull()
  })
})
