<script setup>
import { ref } from 'vue'
import AnswerPanel from './components/AnswerPanel.vue'
import AskForm from './components/AskForm.vue'
import LegalPage from './components/LegalPage.vue'
import PrivacyPage from './components/PrivacyPage.vue'
import FeedbackPage from './components/FeedbackPage.vue'
import SourceReaderPage from './components/SourceReaderPage.vue'
import { icpNumber, policeRecordNumber } from './complianceConfig.js'
import { createConversationClient } from './conversationClient.js'

const pagePath = window.location.pathname.replace(/\/+$/, '') || '/'
const sourcePath = pagePath.match(/^\/sources\/(\d+)$/)

const recommendedQuestions = [
  '转软件工程机考要做什么准备？',
  '转人工智能专业有什么困难？',
  '数理大类有哪些分流方向？',
  '如何分流进入汉语言文学？',
  '法学院转专业考核方式？',
  '软件学院转专业面试真题？',
]

const question = ref('')
const loading = ref(false)
const answer = ref('')
const sources = ref([])
const error = ref('')
const turns = ref([])
const conversation = createConversationClient()

async function askQuestion() {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || loading.value) {
    return
  }

  error.value = ''
  answer.value = ''
  sources.value = []
  loading.value = true

  try {
    const data = await conversation.ask(normalizedQuestion)
    turns.value.push({ question: normalizedQuestion, answer: data.answer, sources: data.sources })
    if (turns.value.length > 8) turns.value.shift()
    question.value = ''
  } catch (failure) {
    error.value = failure.message || '请求失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function newConversation() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    await conversation.reset()
    turns.value = []
    question.value = ''
    answer.value = ''
    sources.value = []
  } catch (failure) { error.value = failure.message || '无法新建对话，请稍后重试。' }
  finally { loading.value = false }
}

function selectQuestion(value) {
  question.value = value
}
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <div class="brand-mark" aria-hidden="true">N</div>
      <div>
        <p class="brand-name">NJU Compass</p>
        <p class="brand-subtitle">南大校园知识助手</p>
      </div>
    </header>

    <main class="main-content">
      <LegalPage v-if="pagePath === '/legal'" />
      <PrivacyPage v-else-if="pagePath === '/privacy'" />
      <FeedbackPage v-else-if="pagePath === '/feedback'" />
      <SourceReaderPage v-else-if="sourcePath" :document-id="sourcePath[1]" />
      <template v-else>
        <section class="intro" aria-labelledby="page-title">
          <p class="eyebrow">CAMPUS KNOWLEDGE SEARCH</p>
          <h1 id="page-title">南京大学校园知识助手</h1>
          <p class="nonofficial-badge">非官方个人项目 · AI 生成内容</p>
          <p class="intro-copy">
            汇集培养方案、转专业政策、学院指南与学习经验，通过检索相关资料提供带来源的校园知识问答。
          </p>
        </section>

        <AskForm
          v-model="question"
          :loading="loading"
          :recommended-questions="recommendedQuestions"
          @submit="askQuestion"
          @select-question="selectQuestion"
        />

        <div class="conversation-controls">
          <p>可接着追问 · 仅使用有限近期上下文，闲置后自动过期</p>
          <button type="button" class="new-conversation-button" :disabled="loading" @click="newConversation">新对话</button>
        </div>

        <section v-for="(turn, index) in turns" :key="turn" class="conversation-turn">
          <p class="conversation-question">{{ turn.question }}</p>
          <AnswerPanel :answer="turn.answer" :sources="turn.sources" :source-heading-id="`sources-heading-${index}`" />
        </section>

        <AnswerPanel
          :answer="answer"
          :sources="sources"
          :loading="loading"
          :error="error"
        />
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
