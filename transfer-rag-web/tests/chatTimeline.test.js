import test from 'node:test'
import assert from 'node:assert/strict'
import { initialChatState, createChatTimeline } from '../src/chatTimeline.js'
const result = { answer: '合成回答 [S1]', sources: [{ citationId: 'S1', documentId: 900001 }] }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }

test('question appears immediately with a pending answer, then receives its own sources', async () => {
  const request = deferred(), state = initialChatState()
  const timeline = createChatTimeline({ ask: () => request.promise }, state)
  state.draft = '问题一'
  const sent = timeline.send()
  assert.equal(state.turns[0].question, '问题一'); assert.equal(state.turns[0].status, 'pending')
  assert.equal(state.draft, ''); assert.equal(state.sending, true)
  request.resolve(result); await sent
  assert.equal(state.turns[0].answer, result.answer); assert.deepEqual(state.turns[0].sources, result.sources)
  assert.equal(state.sending, false)
})
test('all page turns survive more than eight questions, no browser persistence', async () => {
  const state = initialChatState(), timeline = createChatTimeline({ ask: async () => result }, state)
  for (let i = 0; i < 30; i++) { state.draft = `问题${i}`; await timeline.send() }
  assert.equal(state.turns.length, 30); assert.equal(state.turns[0].question, '问题0')
  assert.equal(new Set(state.turns.map(turn => turn.id)).size, 30)
})
test('failure and clarification belong to the original turn and preserve editable question, no auto retry', async () => {
  let calls = 0
  const state = initialChatState(), timeline = createChatTimeline({ ask: async () => { calls++; throw new Error('请明确专业后再提问。') } }, state)
  state.draft = '那考试呢'; await timeline.send()
  assert.equal(calls, 1); assert.equal(state.turns[0].status, 'error')
  assert.match(state.turns[0].error, /明确专业/); assert.equal(state.draft, '那考试呢')
})
test('reset during an active request clears immediately, waits, and never inserts late success', async () => {
  const request = deferred(), state = initialChatState(); let resets = 0, sends = 0
  const timeline = createChatTimeline({ ask: () => { sends++; return request.promise }, reset: async () => { resets++ } }, state)
  state.draft = '旧问题'; const sent = timeline.send(); await Promise.resolve()
  const reset = timeline.reset()
  assert.equal(state.turns.length, 0); assert.equal(state.resetting, true); assert.equal(resets, 0)
  state.draft = '新问题'; await timeline.send(); assert.equal(sends, 1)
  request.resolve(result); await sent; await reset
  assert.equal(resets, 1); assert.equal(state.turns.length, 0); assert.equal(state.resetRequired, false)
})
test('late failure cannot restore an old question or error into the reset window', async () => {
  const request = deferred(), state = initialChatState()
  const timeline = createChatTimeline({ ask: () => request.promise, reset: async () => {} }, state)
  state.draft = '旧问题'; const sent = timeline.send(); await Promise.resolve(); const reset = timeline.reset()
  request.reject(new Error('旧失败')); await sent; await reset
  assert.equal(state.turns.length, 0); assert.equal(state.draft, ''); assert.equal(state.controlError, '')
})
test('failed reset blocks all questions until explicit server reset succeeds', async () => {
  const state = initialChatState(); let resets = 0, asks = 0
  const timeline = createChatTimeline({ ask: async () => { asks++; return result }, reset: async () => { if (++resets === 1) throw new Error('server busy') } }, state)
  await timeline.reset(); assert.equal(state.resetRequired, true); assert.match(state.controlError, /重置/)
  state.draft = '新问题'; await timeline.send(); assert.equal(asks, 0)
  await timeline.reset(); state.draft = '完整新问题'; await timeline.send()
  assert.equal(asks, 1); assert.equal(state.turns.length, 1)
})
test('concurrent sends and resets never create duplicate operations', async () => {
  const request = deferred(), state = initialChatState(); let asks = 0, resets = 0
  const timeline = createChatTimeline({ ask: () => { asks++; return request.promise }, reset: async () => { resets++ } }, state)
  state.draft = '问题'; const sent = timeline.send(); await Promise.resolve(); state.draft = '重复'; await timeline.send()
  const reset = timeline.reset(); await timeline.reset(); request.resolve(result); await sent; await reset
  assert.equal(asks, 1); assert.equal(resets, 1)
})
