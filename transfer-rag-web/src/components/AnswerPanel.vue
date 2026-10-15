<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import SourceCard from './SourceCard.vue'
import { referenceDocuments } from '../referenceDocuments.js'
import { decorateCitations } from '../answerCitations.js'

const REFUSAL_ANSWER = '根据当前知识库资料无法确定。'

const props = defineProps({
  answer: { type: String, default: '' },
  sources: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  sourceHeadingId: { type: String, default: 'sources-heading' },
})

const renderedAnswer = computed(() => {
  if (!props.answer) return ''
  const html = DOMPurify.sanitize(marked.parse(props.answer, { breaks: true, gfm: true }), {
    FORBID_TAGS: ['img', 'iframe', 'video', 'audio', 'form'],
  })
  return decorateCitations(html, props.sources, props.sourceHeadingId)
})
const documents = computed(() => referenceDocuments(props.sources))
const isRefusal = computed(() => props.answer.trim() === REFUSAL_ANSWER)
function followCitation(event) {
  const link = event.target.closest('a.body-citation')
  if (!link) return
  event.preventDefault()
  const target = document.getElementById(link.getAttribute('href').slice(1))
  target?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
  target?.focus({ preventScroll: true })
}
</script>

<template>
  <section v-if="loading" class="result-card loading-panel" aria-live="polite">
    <p class="thinking-status" role="status">正在检索资料并整理回答…</p>
    <div class="loading-line loading-line-short"></div>
    <div class="loading-line"></div>
    <div class="loading-line"></div>
  </section>

  <section v-else-if="error" class="result-card error-panel" role="alert">
    <span class="error-symbol" aria-hidden="true">!</span>
    <div>
      <h2>请补充问题，或稍后再试</h2>
      <p>{{ error }}</p>
    </div>
  </section>

  <section v-else-if="answer" class="answer-section" aria-live="polite">
    <article class="result-card answer-card">
      <div class="markdown-body" @click="followCitation" v-html="renderedAnswer"></div>
      <p v-if="isRefusal" class="refusal-note">
        当前知识库可能尚未收录相关资料，请以学校或学院最新官方通知为准。
      </p>
      <details class="answer-disclaimer">
        <summary>AI 生成，仅供参考</summary>
        <p>回答可能存在遗漏、过时或理解偏差。转专业、课程、考试、学籍等事项，请以南京大学及相关院系最新官方通知为准。</p>
      </details>
    </article>

    <section v-if="sources.length > 0" class="sources-section" :aria-labelledby="sourceHeadingId">
      <div class="sources-heading-row">
        <div>
          <h2 :id="sourceHeadingId">参考文档</h2>
        </div>
        <span>{{ documents.length }} 份 · 同一文档已合并</span>
      </div>
      <div class="source-grid">
        <SourceCard v-for="source in documents" :key="source.documentId || source.citationId" :source="source" :citation-scope="sourceHeadingId" />
      </div>
    </section>
  </section>
</template>
