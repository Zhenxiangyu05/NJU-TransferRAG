// Page-local display state only. Server conversation history/TTL and request receipts stay authoritative.
export function initialChatState() {
  return { turns: [], draft: '', sending: false, resetting: false, resetRequired: false, controlError: '' }
}

export function createChatTimeline(client, state) {
  let epoch = 0
  let sequence = 0
  let inFlight = null
  return {
    async send() {
      const question = state.draft.trim()
      if (!question || state.sending || state.resetting || state.resetRequired) return
      const generation = epoch
      const turn = { id: ++sequence, question, answer: '', sources: [], status: 'pending', error: '' }
      state.turns.push(turn)
      state.draft = ''
      state.controlError = ''
      state.sending = true
      // Keep the raw request promise: reset must wait for the client/server operation, not just hide it.
      const operation = Promise.resolve().then(() => client.ask(question))
      inFlight = operation
      try {
        const data = await operation
        if (generation !== epoch) return
        Object.assign(state.turns.find(item => item.id === turn.id), {
          answer: data.answer, sources: data.sources, status: 'complete',
        })
      } catch (failure) {
        if (generation !== epoch) return
        Object.assign(state.turns.find(item => item.id === turn.id), {
          status: 'error', error: failure.message || '请求失败，请稍后重试。',
        })
        state.draft = question
      } finally {
        if (inFlight === operation) inFlight = null
        if (generation === epoch) state.sending = false
      }
    },
    async reset() {
      if (state.resetting) return
      ++epoch // Ignore both late successes and late failures from the old window.
      state.turns = []
      state.draft = ''
      state.sending = false
      state.resetting = true
      state.resetRequired = true
      state.controlError = ''
      try {
        // Aborting fetch would not cancel server/model work. Wait, then use the existing owner-safe reset.
        if (inFlight) await inFlight.catch(() => {})
        await client.reset()
        state.resetRequired = false
      } catch {
        // Never allow a visually empty window to silently reuse old server context after reset fails.
        state.controlError = '新对话尚未完成重置，请稍后再次点击“新对话”。重置成功前不会发送新问题。'
      } finally { state.resetting = false }
    },
  }
}
