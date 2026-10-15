import test from 'node:test'
import assert from 'node:assert/strict'
import { referenceDocuments, createPublicReadingCheck, citationAnchor } from '../src/referenceDocuments.js'
const source = { documentId: 900001, title: '合成资料的真实返回标题', citationId: 'S1', sourcePage: 3, sourceType: 'PERSONAL' }
const response = (id = 900001, availability = 'AVAILABLE') => ({ ok: true, json: async () => ({ documentId: id, title: '合成资料的真实返回标题', format: 'pdf', availability }) })

test('document dedup retains every citation, chunk and page without mutating response', () => {
  const sources = [source, { ...source, citationId: 'S3', sourcePage: 7, chunkId: 12 }, { ...source, documentId: 900002, citationId: 'S2' }]
  const before = JSON.stringify(sources), groups = referenceDocuments(sources)
  assert.equal(groups.length, 2); assert.equal(groups[0].title, source.title)
  assert.deepEqual(groups[0].references.map(item => item.citationId), ['S1', 'S3'])
  assert.equal(groups[0].references[1].sourcePage, 7); assert.equal(groups[0].references[1].chunkId, 12)
  assert.equal(JSON.stringify(sources), before)
})
test('anchors are per answer and reject unsafe citation identifiers', () => {
  assert.equal(citationAnchor('sources-turn-1', 'S3'), 'sources-turn-1-S3')
  assert.notEqual(citationAnchor('sources-turn-1', 'S3'), citationAnchor('sources-turn-2', 'S3'))
  assert.equal(citationAnchor('invalid"', 'S1'), null); assert.equal(citationAnchor('turn-1', '<script>'), null)
})
test('available original requires public metadata approval, never trusts fileAvailable or sourceUrl', async () => {
  const calls = [], check = createPublicReadingCheck(async (url, options) => { calls.push(url); assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error'); return response() })
  assert.equal(await check({ ...source, fileAvailable: true, sourceUrl: 'https://example.invalid/admin/private' }), '/sources/900001?page=3')
  assert.deepEqual(calls, ['/api/public/sources/900001'])
})
test('private, missing, mismatched metadata and network errors never expose an original link', async () => {
  for (const fetcher of [async () => ({ ok: false }), async () => response(900002), async () => response(900001, 'FILE_MISSING'), async () => { throw new Error('offline') }]) {
    assert.equal(await createPublicReadingCheck(fetcher)({ ...source, fileAvailable: true }), null)
  }
})
test('unsupported and curated sources fail closed without constructing links or fetching originals', async () => {
  let calls = 0; const check = createPublicReadingCheck(async () => { calls++; return response() })
  assert.equal(await check({ ...source, sourceType: 'CURATED' }), null)
  assert.equal(await check({ ...source, documentId: '../admin' }), null); assert.equal(calls, 0)
  assert.equal(await createPublicReadingCheck(async () => ({ ok: true, json: async () => ({ documentId: 900001, title: 'fixture', availability: 'AVAILABLE', format: 'html' }) }))(source), null)
})
test('one pending check per document, page-specific links retained, grants expire', async () => {
  let calls = 0, clock = 100
  const check = createPublicReadingCheck(async () => { calls++; return response() }, () => clock)
  const urls = await Promise.all([check(source), check({ ...source, sourcePage: 7 })])
  assert.equal(calls, 1); assert.deepEqual(urls, ['/sources/900001?page=3', '/sources/900001?page=7'])
  clock += 30001; await check(source); assert.equal(calls, 2)
})
