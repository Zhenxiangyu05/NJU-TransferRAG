import test from 'node:test'
import assert from 'node:assert/strict'
import { createConversationClient } from '../src/conversationClient.js'

const reply = (data, status = 200) => ({ ok: status === 200, status, json: async () => data })
const session = (token = 'x'.repeat(43)) => ({ conversationToken: token, nextRequestId: 'nonce-1' })
const answer = (nonce = 'nonce-2') => ({ answer: 'Synthetic answer [S1]', sources: [{ citationId: 'S1', documentId: 16 }], nextRequestId: nonce })

test('one page reuses server capability, only sends question and issued nonce, keeps citations', async () => {
  const requests = []
  const client = createConversationClient(async (url, options) => {
    assert.equal(url, '/api/rag/ask')
    assert.equal(options.redirect, 'error')
    const body = JSON.parse(options.body); requests.push(body)
    return reply(body.action ? session() : answer(requests.length === 2 ? 'nonce-2' : 'nonce-3'))
  })
  assert.equal((await client.ask('软件工程条件？')).sources[0].documentId, 16)
  await client.ask('那考试呢')
  assert.equal(requests.length, 3)
  assert.equal(requests[1].conversationToken, requests[2].conversationToken)
  assert.equal(requests[1].requestId, 'nonce-1')
  assert.equal(requests[2].requestId, 'nonce-2')
  assert.deepEqual(Object.keys(requests[2]).sort(), ['conversationToken', 'question', 'requestId'])
})
test('new conversation resets server association and clears pending nonce', async () => {
  const requests = []
  let opens = 0
  const client = createConversationClient(async (url, options) => {
    const body = JSON.parse(options.body); requests.push(body)
    return reply(body.action ? session(++opens === 1 ? 'x'.repeat(43) : 'y'.repeat(43)) : answer())
  })
  await client.ask('first'); await client.reset(); await client.ask('second')
  assert.equal(requests[2].conversationToken, 'x'.repeat(43))
  assert.equal(requests[3].conversationToken, 'y'.repeat(43))
})
test('network response loss never auto retries, explicit retry reuses the same nonce', async () => {
  const requests = []; let failed = false
  const client = createConversationClient(async (url, options) => {
    const body = JSON.parse(options.body); requests.push(body)
    if (body.action) return reply(session())
    if (!failed) { failed = true; throw new Error('network') }
    return reply(answer())
  })
  await assert.rejects(client.ask('first'))
  assert.equal(requests.length, 2)
  await client.ask('first')
  assert.equal(requests[1].requestId, requests[2].requestId)
})
test('completion failure is visible, next explicit question uses issued replacement nonce', async () => {
  const requests = []
  const client = createConversationClient(async (url, options) => {
    const body = JSON.parse(options.body); requests.push(body)
    if (body.action) return reply(session())
    if (requests.length === 2) return reply({ code: 'CONTEXT_AMBIGUOUS', nextRequestId: 'nonce-2' }, 422)
    return reply(answer('nonce-3'))
  })
  await assert.rejects(client.ask('那考试呢'), /明确专业/)
  await client.ask('软件工程考试考什么？')
  assert.equal(requests[2].requestId, 'nonce-2')
})
test('expired session is never silently replaced to reinterpret an incomplete followup', async () => {
  let calls = 0
  const client = createConversationClient(async () => ++calls === 1 ? reply(session()) : reply({ code: 'CONVERSATION_EXPIRED' }, 410))
  await assert.rejects(client.ask('那考试呢'), /新对话/)
  assert.equal(calls, 2)
})
test('concurrent submission is rejected in page memory', async () => {
  let finish
  const client = createConversationClient(async (url, options) => JSON.parse(options.body).action ? reply(session()) : new Promise(resolve => { finish = () => resolve(reply(answer())) }))
  const first = client.ask('first')
  while (!finish) await new Promise(resolve => setImmediate(resolve))
  await assert.rejects(client.ask('second'), /处理中/)
  finish(); await first
})
