// Capability and request receipts live only in this page's memory, never a URL/cookie/browser storage.
export function createConversationClient(fetcher = fetch) {
  let token = null
  let busy = false
  let pending = null
  let nextRequestId = null
  const messages = {
    CONVERSATION_DISABLED: '当前未启用多轮对话，请稍后使用或通过单轮接口提问。',
    CONVERSATION_EXPIRED: '会话已过期或被清除，请点击“新对话”并重新说明完整问题。',
    CONVERSATION_BUSY: '当前对话仍在处理中，请稍后再试。',
    CONVERSATION_UNAVAILABLE: '会话服务暂不可用，请稍后再试。',
    CONVERSATION_LEASE_LOST: '本次结果未能安全保存，请新建对话并说明完整问题。',
    REQUEST_ID_CONFLICT: '请求标识冲突，请新建对话后重试。',
    REQUEST_ALREADY_PROCESSED: '该请求已处理或结果未知，未重复调用模型；请修改为完整问题后重新提交。',
  }
  async function post(body) {
    const response = await fetcher('/api/rag/ask', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      cache: 'no-store', redirect: 'error', body: JSON.stringify(body),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      if (typeof data.nextRequestId === 'string') { nextRequestId = data.nextRequestId; pending = null }
      const code = typeof data.code === 'string' ? data.code : 'REQUEST_FAILED'
      const text = code.startsWith('CONTEXT_')
        ? '无法可靠理解这次追问，请明确专业、年级及要询问的事项后再提问。'
        : messages[code] || (response.status === 429 ? '提问较频繁，请稍后再试。' : '请求失败，请稍后重试。')
      const error = new Error(text)
      error.code = code
      throw error
    }
    return data
  }
  async function open(previous) {
    const data = await post({ action: 'NEW_CONVERSATION', ...(previous ? { conversationToken: previous } : {}) })
    if (!/^[A-Za-z0-9_-]{43}$/.test(data.conversationToken || '')) throw new Error('会话响应无效，请稍后再试。')
    if (typeof data.nextRequestId !== 'string') throw new Error('会话响应无效，请稍后再试。')
    token = data.conversationToken
    nextRequestId = data.nextRequestId
    pending = null
  }
  return {
    async ask(question) {
      if (busy) throw new Error(messages.CONVERSATION_BUSY)
      const current = question.trim()
      if (!current || current.length > 500) throw new Error('请输入1～500字的问题。')
      busy = true
      try {
        if (!token) await open(null)
        // Only an explicit user resubmission reuses this receipt; the client never retries HTTP itself.
        if (!pending || pending.question !== current) pending = { question: current, requestId: nextRequestId }
        const data = await post({ ...pending, conversationToken: token })
        if (typeof data.answer !== 'string' || !data.answer.trim() || !Array.isArray(data.sources) || typeof data.nextRequestId !== 'string') throw new Error('回答响应无效，请稍后再试。')
        pending = null
        nextRequestId = data.nextRequestId
        return data
      } finally { busy = false }
    },
    async reset() {
      if (busy) throw new Error(messages.CONVERSATION_BUSY)
      busy = true
      try { await open(token) } finally { busy = false }
    },
  }
}
