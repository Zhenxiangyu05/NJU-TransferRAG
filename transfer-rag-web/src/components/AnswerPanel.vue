<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import SourceCard from './SourceCard.vue'

const REFUSAL_ANSWER = '根据当前知识库资料无法确定。'

const props = defineProps({
  answer: { type: String, default: '' },
  sources: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
})

const renderedAnswer = computed(() => {
  if (!props.answer) return ''
  return DOMPurify.sanitize(marked.parse(props.answer, { breaks: true, gfm: true }))
})

const isRefusal = computed(() => props.answer.trim() === REFUSAL_ANSWER)
</script>

<template>
  <section v-if="loading" class="result-card loading-panel" aria-live="polite">
    <div class="loading-line loading-line-short"></div>
    <div class="loading-line"></div>
    <div class="loading-line"></div>
  </section>

  <section v-else-if="error" class="result-card error-panel" role="alert">
    <span class="error-symbol" aria-hidden="true">!</span>
    <div>
      <h2>暂时无法完成查询</h2>
      <p>{{ error }}</p>
    </div>
  </section>

  <section v-else-if="answer" class="answer-section" aria-live="polite">
    <article class="result-card answer-card">
      <div class="section-heading">
        <span class="section-index" aria-hidden="true">答</span>
        <div>
          <p class="section-kicker">辅助回答</p>
          <h2>根据当前知识库</h2>
        </div>
      </div>
      <div class="markdown-body" v-html="renderedAnswer"></div>
      <p v-if="isRefusal" class="refusal-note">
        当前知识库可能尚未收录相关资料，请以学校或学院最新官方通知为准。
      </p>
    </article>

    <section v-if="sources.length > 0" class="sources-section" aria-labelledby="sources-heading">
      <div class="sources-heading-row">
        <div>
          <p class="section-kicker">EVIDENCE</p>
          <h2 id="sources-heading">回答依据</h2>
        </div>
        <span>{{ sources.length }} 条资料</span>
      </div>
      <div class="source-grid">
        <SourceCard v-for="source in sources" :key="source.citationId" :source="source" />
      </div>
    </section>
  </section>
</template>
