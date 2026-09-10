<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, required: true },
  loading: { type: Boolean, default: false },
  recommendedQuestions: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'submit', 'selectQuestion'])
const canSubmit = computed(() => props.modelValue.trim().length > 0 && !props.loading)

function updateQuestion(event) {
  emit('update:modelValue', event.target.value)
}

function submit() {
  if (canSubmit.value) emit('submit')
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

function selectQuestion(question) {
  if (!props.loading) emit('selectQuestion', question)
}
</script>

<template>
  <section class="ask-card" aria-labelledby="ask-heading">
    <div class="ask-heading-row">
      <div>
        <h2 id="ask-heading">搜索校园知识</h2>
        <p>可询问培养方案、课程信息、院系指南与转专业政策</p>
      </div>
      <span class="keyboard-tip">Enter 发送 · Shift + Enter 换行</span>
    </div>

    <div class="question-field" :class="{ 'is-loading': loading }">
      <label class="sr-only" for="question-input">校园知识问题</label>
      <textarea
        id="question-input"
        :value="modelValue"
        :disabled="loading"
        rows="5"
        placeholder="搜索你关心的校园学习与院系信息……"
        @input="updateQuestion"
        @keydown="handleKeydown"
      />
      <div class="submit-row">
        <p v-if="loading" class="loading-copy" role="status">
          <span class="spinner" aria-hidden="true"></span>
          正在检索并分析资料…
        </p>
        <span v-else class="input-hint">回答将附带知识库来源</span>
        <button type="button" class="submit-button" :disabled="!canSubmit" @click="submit">
          {{ loading ? '分析中' : '查询资料' }}
        </button>
      </div>
    </div>

    <div class="recommendations">
      <p class="recommendation-label">你可以这样问</p>
      <div class="recommendation-list">
        <button
          v-for="item in recommendedQuestions"
          :key="item"
          type="button"
          :disabled="loading"
          @click="selectQuestion(item)"
        >
          {{ item }}
        </button>
      </div>
    </div>
  </section>
</template>
