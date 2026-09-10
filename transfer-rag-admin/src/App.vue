<script setup>
import { computed, reactive, ref } from 'vue'

const ACCEPTED_EXTENSIONS = new Set(['pdf', 'docx', 'md', 'txt'])
const SOURCE_TYPES = ['OFFICIAL', 'OFFICIAL_PDF', 'GITHUB', 'COMMUNITY', 'PERSONAL']
const SCOPES = ['DEPARTMENT', 'GLOBAL']
const DEPARTMENT_OPTIONS = [
  '文学院',
  '历史学院',
  '法学院',
  '哲学学院',
  '新闻传播学院',
  '政府管理学院',
  '信息管理学院',
  '社会学院',
  '商学院',
  '外国语学院',
  '数学学院',
  '物理学院',
  '化学学院',
  '生命科学学院',
  '地球科学与工程学院',
  '地理与海洋科学学院',
  '大气科学学院',
  '电子科学与工程学院',
  '现代工程与应用科学学院',
  '环境学院',
  '天文与空间科学学院',
  '计算机学院',
  '医学院',
  '匡亚明学院',
  '软件学院',
  '教育研究院',
  '工程管理学院',
  '海外教育学院',
  '建筑与城市规划学院',
  '人工智能学院',
  '国际关系学院',
  '交流学生',
  '南京赫尔辛基大气与地球系统科学学院',
  '前沿科学学院',
  '大学外语部',
  '体育部',
  '马克思主义学院',
  '艺术学院',
  '大学计算机基础教学部',
  '中美文化研究中心',
  '心理健康教育与研究中心',
  '中国思想家研究中心',
  '机器人与自动化学院',
  '现代分析中心',
  '能源与资源学院',
  '人文社会科学高级研究院',
  '人民武装部',
  '模式动物遗传研究中心',
  '未来技术学院',
  '创新创业课程',
  '共青团南京大学委员会',
  '南京大学医院',
  '就业指导中心',
  '生物医学工程学院',
  '国际化工作处（台港澳事务办公室）',
  '智能软件与工程学院',
  '创新创业与成果转化工作办公室',
  '中华文化研究院',
  '智能科学与技术学院',
  '文化素质选修',
  '数字经济与管理学院',
  '集成电路学院',
  '*人文科学试验班',
  '*社会科学试验班',
  '*理科试验班（地球科学与资源环境类）',
  '*理科试验班（数理科学类）',
  '*理科试验班（化学与生命科学类）',
  '*工科试验班',
  '*经济管理试验班',
  '本科生院',
  '*技术科学试验班',
  '全球人文研究院',
  '新生学院',
  '科研设施共享中心',
  '现代生物研究院',
]
const MAX_FILE_SIZE = 20 * 1024 * 1024
const QUESTION_APP_URL = import.meta.env.VITE_QUESTION_APP_URL || 'http://localhost:5173/'

const metadata = reactive({
  title: '',
  department: '',
  year: new Date().getFullYear(),
  sourceType: 'COMMUNITY',
  scope: 'DEPARTMENT',
})

const fileInput = ref(null)
const selectedFile = ref(null)
const dragActive = ref(false)
const status = ref('IDLE')
const result = ref(null)
const pageError = ref('')
const errorDetail = ref('')

const importing = computed(() => status.value === 'IMPORTING')
const isOfficial = computed(() => (
  metadata.sourceType === 'OFFICIAL' || metadata.sourceType === 'OFFICIAL_PDF'
))
const canImport = computed(() => (
  selectedFile.value
  && metadata.title.trim()
  && metadata.department.trim()
  && metadata.year !== null
  && metadata.year !== ''
  && !importing.value
))

function openFilePicker() {
  if (!importing.value) fileInput.value?.click()
}

function onFileChange(event) {
  chooseFile(event.target.files)
  event.target.value = ''
}

function onDrop(event) {
  dragActive.value = false
  if (!importing.value) chooseFile(event.dataTransfer.files)
}

function chooseFile(fileList) {
  clearMessages()
  const files = Array.from(fileList || [])
  if (files.length === 0) return

  const file = files[0]
  const extension = getExtension(file.name)

  if (!ACCEPTED_EXTENSIONS.has(extension)) {
    pageError.value = '不支持该文件类型，请选择 PDF、DOCX、MD 或 TXT 文件。'
    return
  }
  if (file.size > MAX_FILE_SIZE) {
    pageError.value = '文件超过 20 MB，请压缩或拆分后再导入。'
    return
  }
  if (file.size === 0) {
    pageError.value = '不能导入空文件。'
    return
  }

  selectedFile.value = file
  metadata.title = stripExtension(file.name)
  status.value = 'READY'
  result.value = null

  if (files.length > 1) {
    errorDetail.value = '当前页面一次处理一个文件，已选择拖入列表中的第一个文件。'
  }
}

function removeFile() {
  if (importing.value) return
  selectedFile.value = null
  metadata.title = ''
  status.value = 'IDLE'
  result.value = null
  clearMessages()
}

function continueUpload() {
  removeFile()
  metadata.department = ''
  requestAnimationFrame(() => openFilePicker())
}

async function startImport() {
  clearMessages()

  if (!selectedFile.value) {
    pageError.value = '请先选择需要导入的文件。'
    return
  }
  if (!metadata.title.trim()) {
    pageError.value = '请填写资料标题。'
    return
  }
  if (!metadata.department.trim()) {
    pageError.value = '请填写所属学院或部门。'
    return
  }
  if (metadata.year === null || metadata.year === '') {
    pageError.value = '请填写资料年份。'
    return
  }
  if (importing.value) return

  status.value = 'IMPORTING'
  result.value = null

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('title', metadata.title.trim())
  formData.append('department', metadata.department.trim())
  formData.append('year', String(metadata.year))
  formData.append('sourceType', metadata.sourceType)
  formData.append('scope', metadata.scope)

  try {
    const response = await fetch('/api/documents/import', {
      method: 'POST',
      body: formData,
    })
    const responseBody = await readJsonResponse(response)

    if (!response.ok) {
      throw new UserFacingError(safeServerMessage(responseBody, response.status))
    }
    if (!responseBody || responseBody.status !== 'SUCCESS' || responseBody.documentId == null) {
      throw new UserFacingError('后端返回的导入结果格式不正确。')
    }

    result.value = responseBody
    status.value = 'SUCCESS'
  } catch (error) {
    status.value = 'FAILED'
    pageError.value = '导入失败，请检查文件或后端服务。'
    if (error instanceof UserFacingError) {
      errorDetail.value = error.message
    } else if (error instanceof TypeError) {
      errorDetail.value = '无法连接后端服务，请确认 Spring Boot 已在 8080 端口运行。'
    } else {
      errorDetail.value = '请求未能完成，请稍后重试。'
    }
  }
}

async function readJsonResponse(response) {
  const raw = await response.text()
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    throw new UserFacingError('后端响应格式异常。')
  }
}

function safeServerMessage(body, httpStatus) {
  if (httpStatus === 413) return '文件超过后端允许的大小限制。'
  const raw = typeof body?.error === 'string'
    ? body.error
    : (typeof body?.message === 'string' ? body.message : '')
  const clean = raw.replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim()
  if (!clean || clean.includes('Exception') || clean.includes(' at ')) {
    return `后端处理失败（HTTP ${httpStatus}）。`
  }
  return clean.slice(0, 180)
}

function clearMessages() {
  pageError.value = ''
  errorDetail.value = ''
}

function getExtension(fileName) {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex >= 0 ? fileName.slice(dotIndex + 1).toLowerCase() : ''
}

function stripExtension(fileName) {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex > 0 ? fileName.slice(0, dotIndex) : fileName
}

function fileType(fileName) {
  return getExtension(fileName).toUpperCase()
}

function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / (1024 ** unitIndex)
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}

function openQuestionApp() {
  window.open(QUESTION_APP_URL, '_blank', 'noopener,noreferrer')
}

class UserFacingError extends Error {}
</script>

<template>
  <main class="page-shell">
    <header class="page-header">
      <div class="brand-mark" aria-hidden="true">NC</div>
      <div class="header-copy">
        <p class="eyebrow">KNOWLEDGE BASE ADMIN</p>
        <h1>NJU Compass 知识库管理</h1>
        <p>导入资料后，系统将自动完成解析、文本切分与向量索引。</p>
      </div>
      <span class="admin-badge">仅供维护人员使用</span>
    </header>

    <section class="workspace-card" aria-labelledby="upload-heading">
      <div class="section-heading">
        <div>
          <span class="step-number">01</span>
          <div>
            <h2 id="upload-heading">选择知识库文件</h2>
            <p>一次导入一个文件，最大 20 MB</p>
          </div>
        </div>
      </div>

      <div
        v-if="!selectedFile"
        class="drop-zone"
        :class="{ 'drop-zone-active': dragActive }"
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
          :disabled="importing"
          @change="onFileChange"
        />
        <div class="upload-icon" aria-hidden="true">↑</div>
        <strong>{{ dragActive ? '松开即可选择文件' : '将文件拖到这里' }}</strong>
        <span>或点击选择本地文件</span>
        <div class="format-list" aria-label="支持的文件格式">
          <span>PDF</span><span>DOCX</span><span>MD</span><span>TXT</span>
        </div>
      </div>

      <div v-else class="selected-file">
        <div class="file-type">{{ fileType(selectedFile.name) }}</div>
        <div class="file-copy">
          <strong>{{ selectedFile.name }}</strong>
          <span>{{ formatFileSize(selectedFile.size) }} · 已准备导入</span>
        </div>
        <button class="quiet-button" type="button" :disabled="importing" @click="removeFile">移除</button>
      </div>

      <div v-if="pageError" class="message message-error" role="alert">
        <strong>{{ pageError }}</strong>
        <span v-if="errorDetail">{{ errorDetail }}</span>
      </div>
      <p v-else-if="errorDetail" class="inline-note">{{ errorDetail }}</p>
    </section>

    <section v-if="selectedFile" class="workspace-card" aria-labelledby="metadata-heading">
      <div class="section-heading">
        <div>
          <span class="step-number">02</span>
          <div>
            <h2 id="metadata-heading">填写资料信息</h2>
            <p>标题已根据文件名自动生成，可以继续修改</p>
          </div>
        </div>
      </div>

      <div class="metadata-grid">
        <label class="field field-title">
          <span>资料标题 <em>*</em></span>
          <input v-model="metadata.title" type="text" :disabled="importing" />
        </label>
        <label class="field field-department">
          <span>所属学院 / 单位 <em>*</em></span>
          <select v-model="metadata.department" :disabled="importing">
            <option disabled value="">请选择所属学院 / 单位</option>
            <option
              v-for="department in DEPARTMENT_OPTIONS"
              :key="department"
              :value="department"
            >
              {{ department }}
            </option>
          </select>
          <small>请选择资料所属学院/单位，而不是专业名称。例如：汉语言文学应选择‘文学院’。</small>
        </label>
        <label class="field">
          <span>资料年份 <em>*</em></span>
          <input v-model.number="metadata.year" type="number" min="1900" max="2200" :disabled="importing" />
        </label>
        <label class="field">
          <span>来源类型 <em>*</em></span>
          <select v-model="metadata.sourceType" :disabled="importing">
            <option v-for="type in SOURCE_TYPES" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label class="field">
          <span>资料范围 <em>*</em></span>
          <select v-model="metadata.scope" :disabled="importing">
            <option v-for="scope in SCOPES" :key="scope" :value="scope">{{ scope }}</option>
          </select>
          <small>学院专属资料选 DEPARTMENT，全校资料选 GLOBAL。</small>
        </label>
      </div>

      <p v-if="isOfficial" class="official-note">请仅将学校或学院正式发布的资料标记为官方来源。</p>
    </section>

    <section v-if="selectedFile" class="workspace-card action-card" aria-labelledby="process-heading">
      <div class="section-heading compact-heading">
        <div>
          <span class="step-number">03</span>
          <div>
            <h2 id="process-heading">建立知识索引</h2>
            <p>后端会一次完成完整导入链路</p>
          </div>
        </div>
      </div>

      <ol class="process-list" :class="{ processing: importing, complete: status === 'SUCCESS' }">
        <li><i></i><span>{{ status === 'SUCCESS' ? '文件已保存' : '上传并解析' }}</span></li>
        <li><i></i><span>{{ status === 'SUCCESS' ? '文档已切分' : '切分文档' }}</span></li>
        <li><i></i><span>{{ status === 'SUCCESS' ? '向量索引已建立' : '创建向量索引' }}</span></li>
        <li><i></i><span>{{ status === 'SUCCESS' ? '已进入知识库' : '写入知识库' }}</span></li>
      </ol>

      <div v-if="importing" class="loading-note" role="status">
        <span class="spinner" aria-hidden="true"></span>
        <div>
          <strong>正在解析并建立知识索引，这可能需要一些时间…</strong>
          <span>PDF OCR 和 Embedding 期间请不要关闭页面或重复提交。</span>
        </div>
      </div>

      <div v-if="status === 'SUCCESS' && result" class="success-panel" role="status">
        <div class="success-mark" aria-hidden="true">✓</div>
        <div class="success-copy">
          <p class="success-label">{{ result.duplicate ? '已复用现有文档' : '导入成功' }}</p>
          <h3>{{ result.duplicate ? '该文件已存在，已复用知识库中的现有文档。' : '资料已进入知识库，可以进行 RAG 问答。' }}</h3>
          <dl>
            <div><dt>文件</dt><dd>{{ selectedFile.name }}</dd></div>
            <div><dt>Document ID</dt><dd>{{ result.documentId }}</dd></div>
            <template v-if="!result.duplicate">
              <div><dt>Chunks</dt><dd>{{ result.chunkCount }}</dd></div>
              <div><dt>Indexed</dt><dd>{{ result.indexedChunks }}</dd></div>
            </template>
          </dl>
          <p v-if="result.duplicate" class="duplicate-note">SHA-256 校验命中了已有文件，本次没有重复创建 Document、Chunk 或 Qdrant Point。</p>
        </div>
      </div>

      <div class="action-row">
        <p v-if="status !== 'SUCCESS'">一次提交即可完成上传、Chunk 和 Qdrant 索引。</p>
        <div class="action-buttons">
          <button v-if="status === 'SUCCESS'" class="secondary-button" type="button" @click="continueUpload">继续上传</button>
          <button v-if="status === 'SUCCESS'" class="primary-button" type="button" @click="openQuestionApp">打开问答页面</button>
          <button v-else class="primary-button" type="button" :disabled="!canImport" @click="startImport">
            <span v-if="importing" class="spinner spinner-light" aria-hidden="true"></span>
            {{ importing ? '正在导入…' : (status === 'FAILED' ? '重新导入' : '导入知识库') }}
          </button>
        </div>
      </div>
    </section>

    <footer class="page-footer">知识库管理端，仅供维护人员使用。请确认资料来源、年份与适用范围后再导入。</footer>
  </main>
</template>
