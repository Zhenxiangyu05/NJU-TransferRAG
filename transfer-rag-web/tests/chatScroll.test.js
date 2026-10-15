import test from 'node:test'
import assert from 'node:assert/strict'
import { isNearBottom, connectChatScroll } from '../src/chatScroll.js'
function fixture() {
  let resize, frame, cancelled = false, disconnected = false
  const handlers = {}
  const view = { scrollHeight: 1000, clientHeight: 400, scrollTop: 600,
    getBoundingClientRect: () => ({ top: 0 }), addEventListener: (name, fn) => { handlers[name] = fn }, removeEventListener: () => {} }
  const turn = { top: -100, isConnected: true, getBoundingClientRect: () => ({ top: turn.top, bottom: turn.top + 300 }) }
  const environment = { ResizeObserver: class { constructor(fn) { resize = fn } observe() {} disconnect() { disconnected = true } },
    requestAnimationFrame: fn => { frame = fn; return 1 }, cancelAnimationFrame: () => { cancelled = true } }
  const changes = [], owner = connectChatScroll(view, { querySelectorAll: () => [turn] }, value => changes.push(value), environment)
  return { view, turn, owner, changes, scroll: () => { handlers.wheel({ type: 'wheel' }); handlers.scroll() }, layoutScroll: () => handlers.scroll(), resize: () => { resize(); frame() }, disposed: () => disconnected && cancelled }
}
test('near bottom threshold handles short and long conversations', () => {
  assert.equal(isNearBottom({ scrollHeight: 300, clientHeight: 400, scrollTop: 0 }), true)
  assert.equal(isNearBottom({ scrollHeight: 1000, clientHeight: 400, scrollTop: 510 }), true)
  assert.equal(isNearBottom({ scrollHeight: 1000, clientHeight: 400, scrollTop: 300 }), false)
})
test('following reader stays at bottom when answer and reference heights increase', () => {
  const f = fixture(); f.view.scrollHeight = 1200; f.resize(); assert.equal(f.view.scrollTop, 800)
})
test('reader browsing upward does not get dragged down by new messages', () => {
  const f = fixture(); f.view.scrollTop = 250; f.scroll(); f.view.scrollHeight = 1400; f.resize()
  assert.equal(f.view.scrollTop, 250); assert.equal(f.changes.at(-1), false)
})
test('reference expansion above viewport preserves visible turn offset', () => {
  const f = fixture(); f.view.scrollTop = 250; f.scroll(); f.turn.top += 120; f.view.scrollHeight += 120; f.resize()
  assert.equal(f.view.scrollTop, 370)
})
test('keyboard-sized viewport keeps following reader at bottom, explicit bottom reattaches', () => {
  const f = fixture(); f.view.clientHeight = 220; f.resize(); assert.equal(f.view.scrollTop, 780)
  f.view.scrollTop = 100; f.scroll(); f.owner.bottom(); assert.equal(f.view.scrollTop, 780); assert.equal(f.changes.at(-1), true)
  f.owner.refresh(); f.owner.destroy(); assert.equal(f.disposed(), true)
})
test('layout-generated scroll before a resize observation cannot detach the following reader', () => {
  const f = fixture(); f.view.clientHeight = 220; f.view.scrollTop = 500; f.layoutScroll(); f.resize()
  assert.equal(f.view.scrollTop, 780); assert.equal(f.changes.at(-1), true)
})
