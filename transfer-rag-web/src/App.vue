<script setup>
import { ref } from 'vue'
import AnswerPanel from './components/AnswerPanel.vue'
import AskForm from './components/AskForm.vue'

const recommendedQuestions = [
  '2026级智能科学与技术专业总学分是多少？',
  '软件工程转专业有哪些准入要求？',
  '电子专业导学课有什么修读要求？',
  '2025级数理大类包含哪些学院方向？',
  '地学大类选课时数学层次怎么选？',
  '计算机学院转专业综合考核有哪些环节？',
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
      <section class="intro" aria-labelledby="page-title">
        <p class="eyebrow">CAMPUS KNOWLEDGE SEARCH</p>
        <h1 id="page-title">基于校园资料的可信知识检索与问答</h1>
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
    </main>

    <footer class="site-footer">
      本平台为非官方校园知识检索工具，回答基于已收录资料生成。涉及培养方案、转专业、课程安排等重要事项，请以南京大学及相关院系最新官方通知为准。
    </footer>
  </div>
</template>
