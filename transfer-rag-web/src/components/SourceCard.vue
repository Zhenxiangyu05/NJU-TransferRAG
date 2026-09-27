<script setup>
import { computed } from 'vue'

const props = defineProps({
  source: { type: Object, required: true },
})

const sourceLabel = computed(() => {
  if (props.source.sourceType === 'PERSONAL') return '个人经验'
  if (props.source.sourceType === 'CURATED') return '整理知识'
  if (props.source.official) return '官方'
  return {
    PERSONAL: '个人经验',
    COMMUNITY: '社区资料',
    GITHUB: 'GitHub资料',
  }[props.source.sourceType] || '参考资料'
})

const sourceTypeText = computed(() => ({
  OFFICIAL: '官方资料',
  OFFICIAL_PDF: '官方 PDF',
  PERSONAL: '个人整理',
  CURATED: '整理知识（非官方）',
  COMMUNITY: '社区资料',
  GITHUB: 'GitHub 资料',
}[props.source.sourceType] || props.source.sourceType || '类型未标注'))

const sourcePageUrl = computed(() => {
  if (typeof props.source.sourceUrl !== 'string' || !props.source.sourceUrl.trim()) return null
  try {
    const url = new URL(props.source.sourceUrl)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
})
</script>

<template>
  <article class="source-card">
    <div class="source-card-topline">
      <span class="citation-id">[{{ source.citationId || '—' }}]</span>
      <span class="source-badge" :class="{ official: source.official }">{{ sourceLabel }}</span>
    </div>
    <h3>《{{ source.title || '未命名资料' }}》</h3>
    <dl class="source-details">
      <div><dt>来源类型</dt><dd>{{ sourceTypeText }}</dd></div>
      <div><dt>学院</dt><dd>{{ source.chunkDepartment || source.documentDepartment || '未标注' }}</dd></div>
      <div><dt>专业</dt><dd>{{ source.major || '未标注' }}</dd></div>
      <div><dt>资料年份</dt><dd>{{ source.documentYear || '未标注' }}</dd></div>
      <div v-if="source.policyYear != null"><dt>政策年份</dt><dd>{{ source.policyYear }}</dd></div>
      <div><dt>检索年份</dt><dd>{{ source.effectiveYear || '未标注' }}</dd></div>
    </dl>
    <div v-if="sourcePageUrl" class="source-actions">
      <a
        v-if="sourcePageUrl"
        class="source-link source-link-muted"
        :href="sourcePageUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        来源页面<span aria-hidden="true">↗</span>
      </a>
    </div>
  </article>
</template>
