<script setup>
import { ref } from 'vue'
import AnswerPanel from './components/AnswerPanel.vue'
import AskForm from './components/AskForm.vue'
import LegalPage from './components/LegalPage.vue'
import PrivacyPage from './components/PrivacyPage.vue'
import FeedbackPage from './components/FeedbackPage.vue'
import SourceReaderPage from './components/SourceReaderPage.vue'
import { icpNumber, policeRecordNumber } from './complianceConfig.js'

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
    const response = await fetch('/api/rag/ask', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ question: normalizedQuestion }),
    })

    if (!response.ok) {
      throw new Error('HTTP request failed')
    }

    const data = await response.json()
    if (!data || typeof data.answer !== 'string' || !Array.isArray(data.sources)) {
      throw new Error('Invalid response payload')
    }

    answer.value = data.answer
    sources.value = data.sources
  } catch {
    error.value = '请求失败，请稍后重试。'
  } finally {
    loading.value = false
  }
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
