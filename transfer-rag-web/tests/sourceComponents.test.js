import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'

test('SourceCard links to original Evidence document and page, leaving metadata unchanged', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  try {
    const { default: SourceCard } = await server.ssrLoadModule('/src/components/SourceCard.vue')
    const source = { documentId: 2, chunkId: 456, sourcePage: 3, sourceType: 'PERSONAL',
      title: '<script>unsafe title</script>', documentDepartment: '软件学院', documentYear: 2026,
      effectiveYear: 2026, policyYear: null }
    const html = await renderToString(createSSRApp(SourceCard, { source }))
    assert.ok(html.includes('href="/sources/2?page=3"'))
    assert.ok(html.includes('查看原文'))
    assert.ok(html.includes('软件学院'))
    assert.ok(html.includes('资料年份'))
    assert.ok(!html.includes('政策年份'))
    assert.ok(!html.includes('/sources/22'))
    assert.ok(!html.includes('<script>unsafe title</script>'))
    const curated = await renderToString(createSSRApp(SourceCard, { source: { ...source, sourceType: 'CURATED' } }))
    assert.ok(!curated.includes('查看原文'))
  } finally { await server.close() }
})

test('reader component uses escaped pre text and controlled PDF frame, not HTML rendering', async () => {
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/components/SourceReaderPage.vue', import.meta.url), 'utf8')
  assert.ok(source.includes('<pre class="text-reader">{{ text }}</pre>'))
  assert.ok(!source.includes('v-html'))
  assert.ok(source.includes(':src="fileUrl"'))
  assert.ok(!source.includes('localStorage') && !source.includes('document.cookie'))
  assert.ok(source.includes("info.format === 'docx' && info.availability === 'DOWNLOADABLE'"))
  assert.ok(source.includes('/api/public/sources/${documentId}/download'))
  assert.ok(source.includes("'DOWNLOADABLE'].includes(info.availability)"))
})
