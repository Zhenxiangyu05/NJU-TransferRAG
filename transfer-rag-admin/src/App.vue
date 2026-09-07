<script setup>
import { computed, reactive, ref } from 'vue'

const ACCEPTED_EXTENSIONS = new Set(['pdf', 'docx', 'md', 'txt'])
const SOURCE_TYPES = ['OFFICIAL', 'COMMUNITY', 'GITHUB', 'PERSONAL']

const metadata = reactive({
  department: '',
  year: 2026,
  sourceType: 'OFFICIAL',
})

const fileInput = ref(null)
const fileItems = ref([])
const dragActive = ref(false)
const importing = ref(false)
const pageError = ref('')
let nextFileId = 1

const readyCount = computed(() => fileItems.value.filter(
  (item) => item.status === 'PENDING' || item.status === 'FAILED',
).length)

const canImport = computed(() => (
  readyCount.value > 0
  && metadata.department.trim().length > 0
  && metadata.year !== null
  && metadata.year !== ''
  && !importing.value
))

function openFilePicker() {
  if (!importing.value) {
    fileInput.value?.click()
  }
}

function onFileChange(event) {
  addFiles(event.target.files)
  event.target.value = ''
}

function onDrop(event) {
  dragActive.value = false
  if (!importing.value) {
    addFiles(event.dataTransfer.files)
  }
}

function addFiles(fileList) {
  pageError.value = ''
  const incomingFiles = Array.from(fileList)
  const rejectedFiles = []

  for (const file of incomingFiles) {
    if (!ACCEPTED_EXTENSIONS.has(getExtension(file.name))) {
      rejectedFiles.push(file.name)
      continue
    }

    const duplicate = fileItems.value.some((item) => (
      item.file.name === file.name
      && item.file.size === file.size
      && item.file.lastModified === file.lastModified
    ))

    if (!duplicate) {
      fileItems.value.push({
        id: nextFileId++,
        file,
        status: 'PENDING',
        documentId: null,
        chunkCount: null,
        indexedChunks: null,
        duplicate: false,
        error: '',
      })
    }
  }

  if (rejectedFiles.length > 0) {
    pageError.value = `已忽略不支持的文件：${rejectedFiles.join('、')}`
  }
}

function removeFile(id) {
  if (importing.value) return
  fileItems.value = fileItems.value.filter(
    (item) => item.id !== id || item.status !== 'PENDING',
  )
}

function clearFiles() {
  if (importing.value) return
  fileItems.value = []
  pageError.value = ''
}

async function startImport() {
  pageError.value = ''

  if (!metadata.department.trim()) {
    pageError.value = '请填写所属学院或部门。'
    return
  }

  if (metadata.year === null || metadata.year === '') {
    pageError.value = '请填写资料年份。'
    return
  }

  if (readyCount.value === 0 || importing.value) return

  importing.value = true
  const itemsToImport = fileItems.value.filter(
    (item) => item.status === 'PENDING' || item.status === 'FAILED',
  )
  itemsToImport.forEach((item) => {
    item.status = 'IMPORTING'
    item.documentId = null
    item.chunkCount = null
    item.indexedChunks = null
    item.duplicate = false
    item.error = ''
  })

  const formData = new FormData()
  itemsToImport.forEach((item) => formData.append('files', item.file))
  formData.append('department', metadata.department.trim())
  formData.append('year', String(metadata.year))
  formData.append('sourceType', metadata.sourceType)

  try {
    const response = await fetch('/api/documents/import/batch', {
      method: 'POST',
      body: formData,
    })
    const responseBody = await readJsonResponse(response)

    if (!response.ok) {
      throw new Error(responseBody?.error || `导入请求失败（HTTP ${response.status}）`)
    }

    applyBatchResults(responseBody?.results, itemsToImport)
  } catch (error) {
    const message = error instanceof Error ? error.message : '导入请求失败'
    pageError.value = message
    fileItems.value.forEach((item) => {
      if (item.status === 'IMPORTING') {
        item.status = 'FAILED'
        item.error = message
      }
    })
  } finally {
    importing.value = false
  }
}

async function readJsonResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) return null
  return response.json()
}

function applyBatchResults(results, importedItems) {
  if (!Array.isArray(results)) {
    throw new Error('后端返回的批量导入结果格式不正确')
  }

  importedItems.forEach((item, index) => {
    const result = results[index]
    if (!result) {
      item.status = 'FAILED'
      item.error = '后端未返回该文件的导入结果'
      return
    }

    item.status = result.status === 'SUCCESS' ? 'SUCCESS' : 'FAILED'
    item.documentId = result.documentId ?? null
    item.chunkCount = result.chunkCount ?? null
    item.indexedChunks = result.indexedChunks ?? null
    item.duplicate = result.duplicate === true
    item.error = result.error || ''
  })
}

function getExtension(fileName) {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex >= 0 ? fileName.slice(dotIndex + 1).toLowerCase() : ''
}

function fileType(fileName) {
  return getExtension(fileName).toUpperCase() || 'UNKNOWN'
}

function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  )
  const value = bytes / (1024 ** unitIndex)
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}
</script>

<template>
  <main class="page-shell">
    <header class="page-header">
      <div class="brand-mark">TR</div>
      <div>
        <p class="eyebrow">KNOWLEDGE BASE ADMIN</p>
        <h1>Transfer RAG 知识库管理</h1>
        <p class="header-description">
          批量上传资料，并自动完成解析、文本切分与向量索引。
        </p>
      </div>
    </header>

    <section class="workspace-card" aria-labelledby="metadata-heading">
      <div class="section-heading">
        <div>
          <span class="step-number">01</span>
          <h2 id="metadata-heading">填写资料信息</h2>
        </div>
        <p>本批文件将共用以下元数据</p>
      </div>

      <div class="metadata-grid">
        <label class="field field-wide">
          <span>学院 / 部门 <em>*</em></span>
          <input
            v-model="metadata.department"
            type="text"
            placeholder="例如：软件学院"
            :disabled="importing"
            autocomplete="organization"
          />
        </label>

        <label class="field">
          <span>年份 <em>*</em></span>
          <input
            v-model.number="metadata.year"
            type="number"
            min="1900"
            max="2200"
            :disabled="importing"
          />
        </label>

        <label class="field">
          <span>来源类型 <em>*</em></span>
          <select v-model="metadata.sourceType" :disabled="importing">
            <option v-for="type in SOURCE_TYPES" :key="type" :value="type">
              {{ type }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="workspace-card" aria-labelledby="upload-heading">
      <div class="section-heading">
        <div>
          <span class="step-number">02</span>
          <h2 id="upload-heading">选择知识库文件</h2>
        </div>
        <p>支持一次选择多个文件</p>
      </div>

      <div
        class="drop-zone"
        :class="{ 'drop-zone-active': dragActive, 'drop-zone-disabled': importing }"
        role="button"
        tabindex="0"
        @click="openFilePicker"
        @keydown.enter.prevent="openFilePicker"
        @keydown.space.prevent="openFilePicker"
        @dragenter.prevent="dragActive = true"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="onDrop"
      >
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept=".pdf,.docx,.md,.txt"
          multiple
          :disabled="importing"
          @change="onFileChange"
        />
        <div class="upload-icon" aria-hidden="true">↑</div>
        <strong>{{ dragActive ? '松开即可添加文件' : '将文件拖到这里' }}</strong>
        <span>或点击选择本地文件</span>
        <div class="format-list">
          <span>PDF</span>
          <span>DOCX</span>
          <span>MD</span>
          <span>TXT</span>
        </div>
      </div>

      <p v-if="pageError" class="error-banner" role="alert">
        {{ pageError }}
      </p>
    </section>

    <section class="workspace-card file-section" aria-labelledby="files-heading">
      <div class="section-heading file-heading">
        <div>
          <span class="step-number">03</span>
          <h2 id="files-heading">待导入文件</h2>
          <span class="file-count">{{ fileItems.length }}</span>
        </div>
        <button
          class="text-button"
          type="button"
          :disabled="fileItems.length === 0 || importing"
          @click="clearFiles"
        >
          清空全部
        </button>
      </div>

      <div v-if="fileItems.length === 0" class="empty-state">
        尚未选择文件，请从上方拖入或选择文件。
      </div>

      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>文件</th>
              <th>大小</th>
              <th>类型</th>
              <th>状态</th>
              <th>导入结果</th>
              <th><span class="visually-hidden">操作</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in fileItems" :key="item.id">
              <td class="file-name-cell" :title="item.file.name">
                {{ item.file.name }}
              </td>
              <td>{{ formatFileSize(item.file.size) }}</td>
              <td><span class="type-chip">{{ fileType(item.file.name) }}</span></td>
              <td>
                <span class="status-badge" :class="`status-${item.status.toLowerCase()}`">
                  <i aria-hidden="true"></i>{{ item.status }}
                </span>
              </td>
              <td class="result-cell">
                <template v-if="item.status === 'SUCCESS'">
                  <span>ID {{ item.documentId }}</span>
                  <span v-if="item.duplicate">文件已存在，已复用</span>
                  <template v-else>
                    <span>Chunk {{ item.chunkCount }}</span>
                    <span>索引 {{ item.indexedChunks }}</span>
                  </template>
                </template>
                <span v-else-if="item.status === 'FAILED'" class="row-error">
                  {{ item.error || '导入失败' }}
                </span>
                <span v-else class="muted">—</span>
              </td>
              <td class="action-cell">
                <button
                  v-if="item.status === 'PENDING'"
                  class="remove-button"
                  type="button"
                  :disabled="importing"
                  :aria-label="`移除 ${item.file.name}`"
                  @click="removeFile(item.id)"
                >
                  移除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="submit-row">
        <div class="submit-hint">
          <strong>{{ readyCount }} 个文件待处理</strong>
          <span>失败文件可再次点击导入重试，成功文件不会重复提交</span>
        </div>
        <button
          class="primary-button"
          type="button"
          :disabled="!canImport"
          @click="startImport"
        >
          <span v-if="importing" class="spinner" aria-hidden="true"></span>
          {{ importing ? '正在导入…' : '开始导入' }}
        </button>
      </div>
    </section>
  </main>
</template>
