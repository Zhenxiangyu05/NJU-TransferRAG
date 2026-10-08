import { test } from 'node:test'
import assert from 'node:assert/strict'
import { pollImportTask, validateTask } from '../src/importTaskPolling.js'
const task = status => ({ taskId: 7, documentId: 8, status })
test('polls QUEUED -> PROCESSING -> SUCCEEDED without POST', async () => {
  const responses = [task('PROCESSING'), task('SUCCEEDED')]
  const seen = []
  const done = await pollImportTask(task('QUEUED'), { sleep: async () => {}, onUpdate: t => seen.push(t.status),
    fetcher: async (url, options) => {
      assert.equal(url, 'api/import-tasks/7'); assert.equal(options.method, undefined)
      return { ok: true, json: async () => responses.shift() }
    } })
  assert.equal(done.status, 'SUCCEEDED'); assert.deepEqual(seen, ['QUEUED', 'PROCESSING', 'SUCCEEDED'])
})
test('terminal failure stops polling', async () => {
  let calls = 0
  const done = await pollImportTask(task('FAILED'), { fetcher: async () => calls++ })
  assert.equal(done.status, 'FAILED'); assert.equal(calls, 0)
})
test('network failure preserves caller task and does not resubmit', async () => {
  const initial = task('PROCESSING')
  await assert.rejects(pollImportTask(initial, { sleep: async () => {}, fetcher: async () => ({ ok: false }) }))
  assert.equal(initial.status, 'PROCESSING')
})
test('unmount abort stops further requests', async () => {
  const controller = new AbortController(); controller.abort()
  await assert.rejects(pollImportTask(task('QUEUED'), { signal: controller.signal }))
})
test('invalid task or legacy SUCCESS is not mistaken for durable completion', () => {
  assert.throws(() => validateTask(task('SUCCESS')))
  assert.throws(() => validateTask({ ...task('QUEUED'), taskId: '../documents' }))
})
test('task identity cannot change while polling', async () => {
  await assert.rejects(pollImportTask(task('QUEUED'), { sleep: async () => {},
    fetcher: async () => ({ ok: true, json: async () => ({ ...task('SUCCEEDED'), documentId: 99 }) }) }))
})
