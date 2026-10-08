<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { loadPublicSource, pdfUrl, positiveInteger, sourceTypeText } from '../sourceReading.js'

const props = defineProps({ documentId: { type: String, required: true } })
const page = positiveInteger(new URLSearchParams(window.location.search).get('page'))
const info = ref(null)
const text = ref('')
const error = ref('')
const loading = ref(true)
const abort = new AbortController()
const fileUrl = computed(() => pdfUrl(props.documentId, page))

onMounted(async () => {
  try {
    const result = await loadPublicSource(props.documentId, fetch, abort.signal)
    info.value = result.info
    text.value = result.text
  } catch (failure) {
    if (failure.name !== 'AbortError') error.value = failure.message || '原文暂不可用，请稍后再试。'
  } finally {
    loading.value = false
  }
})
onUnmounted(() => abort.abort())
</script>

<template>
  <article class="legal-page source-reader" aria-labelledby="reader-title">
    <a class="back-link" href="/">← 返回问答页面</a>
    <h1 id="reader-title">{{ info?.title || '原始资料阅读' }}</h1>
    <p v-if="loading" role="status">正在读取原文信息…</p>
    <p v-else-if="error" class="reader-notice" role="status">{{ error }}</p>
    <template v-else-if="info">
      <dl class="reader-details">
        <div><dt>来源类型</dt><dd>{{ sourceTypeText(info.sourceType) }}</dd></div>
        <div><dt>资料年份</dt><dd>{{ info.documentYear ?? '未标注' }}</dd></div>
        <div><dt>引用页码</dt><dd>{{ page ?? '未标注' }}</dd></div>
      </dl>
      <p v-if="!['AVAILABLE', 'DOWNLOADABLE'].includes(info.availability)" class="reader-notice" role="status">{{ info.message || '原文暂不可预览。' }}</p>
      <template v-else-if="info.format === 'pdf'">
        <p class="reader-hint">展示完整 PDF。引用页码定位取决于浏览器阅读器；请以原文件页码为准。如内嵌阅读器无法显示，可<a :href="fileUrl" target="_blank" rel="noopener noreferrer">在新窗口阅读 PDF</a>。</p>
        <iframe class="pdf-reader" :src="fileUrl" :title="`${info.title} 原始 PDF`" referrerpolicy="no-referrer"></iframe>
      </template>
      <template v-else-if="info.format === 'docx' && info.availability === 'DOWNLOADABLE'">
        <p class="reader-hint">DOCX 不做网页转换，以原件下载保留文档内容和版式。</p>
        <p><a class="docx-download" :href="`/api/public/sources/${documentId}/download`" download>下载原始 DOCX 文档</a></p>
      </template>
      <template v-else-if="['md', 'txt'].includes(info.format)">
        <p class="reader-hint">以下为完整原始文本，仅作只读展示。Markdown 保留原始标记，不执行 HTML、脚本，也不加载文档中的外部图片。文本文件没有可靠 PDF 页码。</p>
        <pre class="text-reader">{{ text }}</pre>
      </template>
      <p v-else class="reader-notice">此格式暂不支持完整原文预览。</p>
    </template>
    <p class="reader-hint">资料内容及相关权益归原权利人所有。发现问题可通过<a href="/feedback">纠错 / 侵权反馈</a>联系维护者。</p>
  </article>
</template>

<style scoped>
.source-reader h1 { overflow-wrap: anywhere; }
.reader-details { display: flex; flex-wrap: wrap; gap: 12px 28px; margin: 0 0 24px; }
.reader-details div { min-width: 0; }
.reader-details dt { color: var(--muted); font-size: 12px; }
.reader-details dd { margin: 0; overflow-wrap: anywhere; }
.reader-notice { padding: 18px; border: 1px solid var(--border); border-radius: 15px; background: var(--surface); }
.reader-hint { color: var(--muted); font-size: 13px; }
.pdf-reader { display: block; width: 100%; height: 70vh; min-height: 360px; margin-bottom: 24px; border: 1px solid var(--border); border-radius: 15px; background: var(--surface); }
.text-reader { margin: 0 0 24px; padding: 24px; border: 1px solid var(--border); border-radius: 15px; background: var(--surface); font: inherit; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 680px) { .text-reader { padding: 16px; } .reader-details { gap: 12px 20px; } }
</style>
