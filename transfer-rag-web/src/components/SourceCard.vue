<script setup>
import { computed, ref, watch } from 'vue'
import { citationAnchor, publicReadingCheck } from '../referenceDocuments.js'
import { readingUrl } from '../sourceReading.js'

const props = defineProps({
  source: { type: Object, required: true },
  citationScope: { type: String, default: 'sources-heading' },
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

const originalUrl = ref(null)
const checking = ref(true)
watch(() => props.source, async (source, _, cleanup) => {
  let current = true
  cleanup(() => { current = false })
  originalUrl.value = null
  checking.value = true
  const url = await publicReadingCheck(source)
  if (current) { originalUrl.value = url; checking.value = false }
}, { immediate: true })
const references = computed(() => props.source.references || [props.source])
</script>

<template>
  <article class="source-card">
    <div class="source-card-topline">
      <span v-for="reference in references" :key="reference.citationId" :id="citationAnchor(citationScope, reference.citationId)" class="citation-id" tabindex="-1">
        <a v-if="originalUrl" :href="readingUrl(source.documentId, reference.sourcePage)" :aria-label="`查看引用 ${reference.citationId} 原文`" target="_blank" rel="noopener noreferrer">[{{ reference.citationId || '—' }}]<small v-if="reference.sourcePage"> p.{{ reference.sourcePage }}</small></a>
        <template v-else>[{{ reference.citationId || '—' }}]<small v-if="reference.sourcePage"> p.{{ reference.sourcePage }}</small></template>
      </span>
      <span class="source-badge" :class="{ official: source.official }">{{ sourceLabel }}</span>
    </div>
    <h3><a v-if="originalUrl" :href="originalUrl" target="_blank" rel="noopener noreferrer">{{ source.title || '资料标题未提供' }}<span aria-hidden="true"> ↗</span></a><span v-else>{{ source.title || '资料标题未提供' }}</span></h3>
    <div class="source-access-status">
      <a v-if="originalUrl" class="source-link" :href="originalUrl" target="_blank" rel="noopener noreferrer">查看原文 ↗</a>
      <span v-else>{{ checking ? '正在核验原文访问权限…' : '原文未公开或暂不可用' }}</span>
    </div>
    <details class="source-metadata">
    <summary>资料信息</summary>
    <dl class="source-details">
      <div><dt>来源类型</dt><dd>{{ sourceTypeText }}</dd></div>
      <div><dt>学院</dt><dd>{{ source.chunkDepartment || source.documentDepartment || '未标注' }}</dd></div>
      <div><dt>专业</dt><dd>{{ source.major || '未标注' }}</dd></div>
      <div><dt>资料年份</dt><dd>{{ source.documentYear || '未标注' }}</dd></div>
      <div v-if="source.policyYear != null"><dt>政策年份</dt><dd>{{ source.policyYear }}</dd></div>
      <div><dt>检索年份</dt><dd>{{ source.effectiveYear || '未标注' }}</dd></div>
    </dl>
    </details>
  </article>
</template>
