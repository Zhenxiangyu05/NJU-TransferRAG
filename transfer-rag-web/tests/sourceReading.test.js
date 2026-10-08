import test from 'node:test'
import assert from 'node:assert/strict'
import { loadPublicSource, pdfUrl, positiveInteger, readingUrl, sourceTypeText } from '../src/sourceReading.js'

test('citation route preserves Evidence id and known page without guessing', () => {
  assert.equal(readingUrl(2, 3), '/sources/2?page=3')
  assert.equal(readingUrl(2, null), '/sources/2')
  assert.equal(pdfUrl('2', '3'), '/api/public/sources/2/file#page=3')
})
test('rejects invalid IDs, pages and path injection', () => {
  for (const value of [null, 0, -1, '1.2', '../2', '2?path=secret', '9007199254740992', 'Infinity']) {
    assert.equal(positiveInteger(value), null)
    assert.equal(readingUrl(value, 3), null)
  }
  assert.equal(readingUrl(2, '../3'), '/sources/2')
})
test('PERSONAL/CURATED never labeled official', () => {
  assert.equal(sourceTypeText('PERSONAL'), '个人整理 / 经验资料')
  assert.equal(sourceTypeText('CURATED'), '整理知识（非原始 Evidence）')
})
test('approved text fetches complete raw text without rendering HTML or sending credentials', async () => {
  const calls = []
  const raw = '<script>alert(1)</script>\n![image](https://example.invalid/tracker)'
  const fetcher = async (url, options) => {
    calls.push({ url, options })
    return { ok: true, json: async () => url.endsWith('/text') ? { text: raw }
      : { documentId: 2, title: 'Test fixture', format: 'md', availability: 'AVAILABLE' } }
  }
  assert.equal((await loadPublicSource(2, fetcher)).text, raw)
  assert.deepEqual(calls.map(c => c.url), ['/api/public/sources/2', '/api/public/sources/2/text'])
  assert.ok(calls.every(c => c.options.credentials === 'omit'))
})
test('PDF loads metadata only; does not fetch PDF into JavaScript', async () => {
  let count = 0
  const result = await loadPublicSource(7, async () => {
    count++
    return { ok: true, json: async () => ({ documentId: 7, title: 'Test PDF', format: 'pdf', availability: 'AVAILABLE' }) }
  })
  assert.equal(count, 1)
  assert.equal(result.text, '')
})
test('unapproved document gives safe explanation and never reads text', async () => {
  let count = 0
  await assert.rejects(loadPublicSource(2, async () => { count++; return { ok: false } }), /尚未确认公开/)
  assert.equal(count, 1)
})
test('missing/unsupported file loads metadata but never file contents', async () => {
  for (const availability of ['FILE_MISSING', 'UNSUPPORTED']) {
    let count = 0
    const result = await loadPublicSource(8, async () => {
      count++
      return { ok: true, json: async () => ({ documentId: 8, title: 'Test fixture', format: 'docx', availability }) }
    })
    assert.equal(count, 1)
    assert.equal(result.text, '')
  }
})
test('bad metadata and failed text requests are handled without provider/path leakage', async () => {
  await assert.rejects(loadPublicSource(2, async () => ({ ok: true, json: async () => ({ documentId: 3 }) })), /原文信息/)
  await assert.rejects(loadPublicSource(2, async url => url.endsWith('/text') ? { ok: false }
    : { ok: true, json: async () => ({ documentId: 2, title: 'Test', format: 'txt', availability: 'AVAILABLE' }) }), /原文文本/)
})
