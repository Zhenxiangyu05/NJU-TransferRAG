// Isolated local UI fixtures only. No real document IDs, DB, models or production APIs.
// Run: node tests/previewPublicSources.mjs
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

function fixturePdf() {
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R 6 0 R] /Count 4 >>']
  for (let page = 1; page <= 4; page++) {
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 500 300] /Resources << /Font << /F1 7 0 R >> >> /Contents ${7 + page} 0 R >>`)
  }
  objects.push('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
  for (let page = 1; page <= 4; page++) {
    const stream = `BT /F1 18 Tf 40 200 Td (TEST FIXTURE ONLY - PAGE ${page}) Tj ET`
    objects.push(`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`)
  }
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(pdf))
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xref = Buffer.byteLength(pdf)
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.slice(1).map(n => `${String(n).padStart(10, '0')} 00000 n \n`).join('')}`
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(pdf)
}

const server = await createServer({
  configFile: false,
  plugins: [vue(), {
    name: 'isolated-public-source-ui-fixtures',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if (!request.url.startsWith('/api/')) return next()
        const match = request.url.match(/^\/api\/public\/sources\/(90000[1-4])(\/text|\/file|\/download)?$/)
        response.setHeader('Cache-Control', 'no-store')
        if (!match) { response.statusCode = 404; response.end('{}'); return }
        const id = Number(match[1])
        if (id === 900004) { response.statusCode = 404; response.end('{}'); return }
        if (match[2] === '/download' && id === 900003) {
          response.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')
          response.setHeader('Content-Disposition', "attachment; filename*=UTF-8''fixture.docx")
          response.end(Buffer.from('PK\u0003\u0004LOCAL-TEST-FIXTURE')); return
        }
        if (match[2] === '/file' && id === 900002) {
          response.setHeader('Content-Type', 'application/pdf')
          response.end(fixturePdf()); return
        }
        response.setHeader('Content-Type', 'application/json')
        response.end(JSON.stringify(match[2] === '/text'
          ? { text: '本页为隔离 UI 测试资料，不属于知识库。\n\n<script>alert("must not execute")</script>\n![外部图片](https://example.invalid/tracker)\n\n' + '长行响应式验证'.repeat(25) + '\n\n最后一段：完整文本显示结束。' }
          : { documentId: id, title: '仅用于本地 UI 测试的原文资料', sourceType: 'PERSONAL', documentYear: 2026,
            format: id === 900002 ? 'pdf' : id === 900003 ? 'docx' : 'md',
            availability: id === 900003 ? 'DOWNLOADABLE' : 'AVAILABLE',
            message: id === 900003 ? 'DOCX 提供原始文件下载，不在网页中转换或预览。' : '' }))
      })
    },
  }],
  server: { host: '127.0.0.1', port: 5174, strictPort: true },
})
await server.listen()
console.log('Isolated UI fixtures: http://127.0.0.1:5174/sources/900001')
process.once('SIGINT', async () => { await server.close(); process.exit(0) })
