// Local UI fixture: all API requests terminate here. No keys, production APIs or model calls.
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
let session = 0, nonce = 0
const fixtures = {
  900001: { title: '软件工程转专业备考指南 · 本地展示资料', sourceType: 'OFFICIAL_PDF', format: 'pdf' },
  900002: { title: '学生备考经验记录 · 未公开原件', sourceType: 'PERSONAL', format: 'md' },
}
const server = await createServer({
  configFile: false,
  plugins: [vue(), { name: 'local-chat-fixtures', configureServer(server) {
    server.middlewares.use(async (request, response, next) => {
      if (!request.url.startsWith('/api/')) return next()
      response.setHeader('Content-Type', 'application/json'); response.setHeader('Cache-Control', 'no-store')
      const reply = (body, status = 200) => { response.statusCode = status; response.end(JSON.stringify(body)) }
      const match = request.url.match(/^\/api\/public\/sources\/(\d+)$/)
      if (match) {
        const id = Number(match[1])
        if (id !== 900001) return reply({}, 404)
        await new Promise(resolve => setTimeout(resolve, 150))
        return reply({ documentId: id, ...fixtures[id], availability: 'AVAILABLE' })
      }
      if (request.url !== '/api/rag/ask') return reply({}, 404)
      let text = ''; for await (const chunk of request) text += chunk
      const body = JSON.parse(text)
      if (body.action === 'NEW_CONVERSATION') return reply({ conversationToken: String(++session).padStart(43, 'x'), nextRequestId: `fixture-${++nonce}` })
      await new Promise(resolve => setTimeout(resolve, body.question.includes('延迟') ? 2200 : 250))
      if (body.question.includes('澄清')) return reply({ code: 'CONTEXT_AMBIGUOUS', nextRequestId: `fixture-${++nonce}` }, 422)
      if (body.question.includes('失败')) return reply({ code: 'AI_SERVICE_UNAVAILABLE', nextRequestId: `fixture-${++nonce}` }, 503)
      const sources = [
        { documentId: 900001, chunkId: 910001, citationId: 'S1', sourcePage: 3 },
        { documentId: 900001, chunkId: 910002, citationId: 'S2', sourcePage: 7 },
        { documentId: 900002, chunkId: 910003, citationId: 'S3' },
      ].map(source => ({ ...source, ...fixtures[source.documentId], official: source.documentId === 900001, documentYear: 2026, documentDepartment: '软件学院', fileAvailable: true, sourceUrl: '/admin/private' }))
      const safetyFixture = body.question.includes('安全') ? '\n\n[模型给出的任意链接](https://example.invalid/admin/)\n\n<img src="https://example.invalid/tracker"><script>window.untrustedFixture=true</script>\n\n未知引用[S999]，代码`[S1]`不转换为引用。' : ''
      return reply({ answer: '以下内容仅用于界面展示与隔离测试，不代表真实招生政策。\n\n### 先把基础打稳\n\n- 结合公开资料了解考试范围，再安排基础练习。[S1]\n- 重点记录解题思路，定期复盘错题。[S2]\n- 个人经验只能作为参考，不能替代官方通知。[S3]\n\n建议按自己的基础安排节奏，遇到具体要求时回到本轮参考文档核对。' + safetyFixture, sources, nextRequestId: `fixture-${++nonce}` })
    })
  } }], server: { host: '127.0.0.1', port: 5173, strictPort: true },
})
await server.listen()
console.log('Local chat UI fixtures ready at http://127.0.0.1:5173 (no model calls)')
process.once('SIGINT', async () => { await server.close(); process.exit(0) })
