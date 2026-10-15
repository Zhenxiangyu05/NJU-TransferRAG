<script setup>
import { ref, reactive, watch, nextTick, onMounted, onUnmounted } from 'vue'
import AnswerPanel from './components/AnswerPanel.vue'
import AskForm from './components/AskForm.vue'
import LegalPage from './components/LegalPage.vue'
import PrivacyPage from './components/PrivacyPage.vue'
import FeedbackPage from './components/FeedbackPage.vue'
import SourceReaderPage from './components/SourceReaderPage.vue'
import { icpNumber, policeRecordNumber } from './complianceConfig.js'
import { createConversationClient } from './conversationClient.js'
import { initialChatState, createChatTimeline } from './chatTimeline.js'
import { connectChatScroll } from './chatScroll.js'

const pagePath = window.location.pathname.replace(/\/+$/, '') || '/'
const sourcePath = pagePath.match(/^\/sources\/(\d+)$/)
const isChat = !['/legal', '/privacy', '/feedback'].includes(pagePath) && !sourcePath

const recommendedQuestions = [
  '转软件工程机考要做什么准备？',
  '转人工智能专业有什么困难？',
  '数理大类有哪些分流方向？',
  '如何分流进入汉语言文学？',
  '法学院转专业考核方式？',
  '软件学院转专业面试真题？',
]

const chat = reactive(initialChatState())
const timeline = createChatTimeline(createConversationClient(), chat)
const scrollView = ref(null)
const scrollContent = ref(null)
const following = ref(true)
const viewportHeight = ref(null)
let scrollOwner
const viewport = window.visualViewport
function resizeViewport() { viewportHeight.value = `${viewport?.height || window.innerHeight}px` }
onMounted(() => {
  if (!isChat) return
  document.body.classList.add('chat-page')
  resizeViewport()
  viewport?.addEventListener('resize', resizeViewport)
  window.addEventListener('resize', resizeViewport)
  scrollOwner = connectChatScroll(scrollView.value, scrollContent.value, value => { following.value = value })
})
onUnmounted(() => {
  document.body.classList.remove('chat-page')
  viewport?.removeEventListener('resize', resizeViewport)
  window.removeEventListener('resize', resizeViewport)
  scrollOwner?.destroy()
})
watch(() => [chat.turns.length, chat.sending, chat.resetting], async () => {
  await nextTick()
  if (!chat.turns.length) scrollOwner?.bottom()
  else scrollOwner?.refresh()
})
function selectQuestion(value) { chat.draft = value }
</script>

<template>
  <div class="site-shell" :class="{ 'chat-shell': isChat }" :style="isChat && viewportHeight ? { height: viewportHeight } : undefined">
    <header class="site-header">
      <div class="brand-mark" aria-hidden="true">N</div>
      <div>
        <p class="brand-name">NJU Compass</p>
        <p class="brand-subtitle">南大校园知识助手</p>
      </div>
      <button v-if="isChat" type="button" class="new-conversation-button" :disabled="chat.resetting" @click="timeline.reset">
        <span aria-hidden="true">＋</span> {{ chat.resetting ? '正在重置…' : '新对话' }}
      </button>
    </header>

    <main class="main-content" :class="{ 'chat-main': isChat }">
      <LegalPage v-if="pagePath === '/legal'" />
      <PrivacyPage v-else-if="pagePath === '/privacy'" />
      <FeedbackPage v-else-if="pagePath === '/feedback'" />
      <SourceReaderPage v-else-if="sourcePath" :document-id="sourcePath[1]" />
      <template v-else>
        <div class="chat-stage">
        <section ref="scrollView" class="chat-scroll" tabindex="0" aria-label="当前对话消息">
        <div ref="scrollContent" class="chat-content">
        <section v-if="!chat.turns.length" class="intro chat-welcome" aria-labelledby="page-title">
          <p class="eyebrow">CAMPUS KNOWLEDGE SEARCH</p>
          <h1 id="page-title">南京大学校园知识助手</h1>
          <p class="nonofficial-badge">非官方个人项目 · AI 生成内容</p>
          <p class="intro-copy">
            从一个问题开始，也可以接着追问。回答基于知识库资料，并附上参考文档。
          </p>
          <div class="recommendation-list welcome-questions">
            <button v-for="item in recommendedQuestions" :key="item" type="button" :disabled="chat.resetting || chat.resetRequired" @click="selectQuestion(item)">{{ item }}<span aria-hidden="true">↗</span></button>
          </div>
        </section>
        <p v-if="chat.resetting" class="chat-reset-status" role="status">正在等待上一条请求结束并重置上下文，请稍候…</p>
        <section v-for="turn in chat.turns" :key="turn.id" class="chat-turn" :data-turn-id="turn.id" :aria-label="`第 ${turn.id} 轮问答`">
          <div class="user-message"><span class="sr-only">你：</span><p>{{ turn.question }}</p></div>
          <div class="assistant-message">
            <div class="assistant-label"><span class="assistant-avatar" aria-hidden="true">N</span><span>NJU Compass</span></div>
            <AnswerPanel :answer="turn.answer" :sources="turn.sources" :loading="turn.status === 'pending'" :error="turn.error" :source-heading-id="`sources-turn-${turn.id}`" />
            <button v-if="turn.status === 'error'" type="button" class="edit-question" :disabled="chat.sending || chat.resetting" @click="selectQuestion(turn.question)">重新编辑问题</button>
          </div>
        </section>
        </div>
        </section>
        <button v-if="!following && chat.turns.length" type="button" class="back-to-bottom" @click="scrollOwner?.bottom()">↓ 回到底部</button>
        </div>
        <div class="chat-composer">
          <p v-if="chat.controlError" class="reset-error" role="alert">{{ chat.controlError }}</p>
          <AskForm v-model="chat.draft" :loading="chat.sending" :blocked="chat.resetting || chat.resetRequired" @submit="timeline.send" />
          <p class="context-note">本页保留全部消息 · 模型仅使用最近 4 轮有效上下文，约 30 分钟过期</p>
        </div>
      </template>
    </main>

    <footer class="site-footer">
      <p>NJU Compass 为个人学习与技术实践项目，与南京大学及其学院、部门不存在隶属、授权、合作或官方认可关系。</p>
      <nav class="footer-links" aria-label="站点信息">
        <a href="/legal">免责声明</a>
        <a href="/privacy">隐私说明</a>
        <a href="/feedback">纠错 / 侵权反馈</a>
      </nav>
      <div v-if="icpNumber || policeRecordNumber" class="record-links">
        <a v-if="icpNumber" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ icpNumber }}</a>
        <a v-if="policeRecordNumber" href="https://beian.mps.gov.cn/" target="_blank" rel="noopener noreferrer">{{ policeRecordNumber }}</a>
      </div>
    </footer>
  </div>
</template>
