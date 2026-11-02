<template>
  <div class="chat-page">
    <h2>知识库问答</h2>
    <el-input v-model="question" type="textarea" :rows="2" placeholder="向你的知识库提问…"
              @keydown.enter.exact.prevent="ask" />
    <el-space style="margin: 8px 0">
      <el-button type="primary" :loading="streaming" :disabled="!question.trim()" @click="ask">
        提问（Enter）
      </el-button>
      <el-checkbox v-model="includeDraft">包含未确认草稿</el-checkbox>
    </el-space>

    <el-card v-if="answer || streaming" shadow="never" style="margin-top: 12px">
      <template v-if="streaming && !answer">
        <el-skeleton :rows="4" animated />
        <div class="waiting-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在检索资料并生成回答…
        </div>
      </template>
      <template v-else>
        <MdPreview :modelValue="answer" />
        <span v-if="streaming" class="stream-cursor">▍</span>
      </template>
    </el-card>

    <div v-if="sources.length" class="sources-block">
      <h4>引用来源（{{ sources.length }}）</h4>
      <div v-for="(s, i) in sources" :key="i" class="source-item"
           @click="$router.push(`/docs/${s.documentId}`)">
        <span class="source-index">[{{ i + 1 }}]</span>
        <div class="source-body">
          <div class="source-title">
            <span class="source-title-text">{{ s.title }}</span>
            <el-tag size="small" effect="plain" class="source-score">
              相关度 {{ Math.round((s.score || 0) * 100) }}%
            </el-tag>
          </div>
          <div class="source-snippet">{{ s.snippet }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { Loading } from '@element-plus/icons-vue'

const question = ref('')
const answer = ref('')
const sources = ref([])
const includeDraft = ref(false)
const streaming = ref(false)
let eventSource = null

function ask() {
  if (!question.value.trim() || streaming.value) return
  close()
  answer.value = ''
  sources.value = []
  streaming.value = true

  const params = new URLSearchParams({ question: question.value, includeDraft: includeDraft.value })
  eventSource = new EventSource(`/api/chat?${params}`)
  eventSource.addEventListener('sources', (e) => {
    sources.value = JSON.parse(e.data)
  })
  eventSource.addEventListener('token', (e) => {
    answer.value += e.data
  })
  eventSource.addEventListener('done', () => {
    streaming.value = false
    close()
  })
  eventSource.onerror = () => {
    streaming.value = false
    close()
  }
}

function close() {
  eventSource?.close()
  eventSource = null
}

onBeforeUnmount(close)
</script>

<style scoped>
.chat-page { max-width: 860px; }
.waiting-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}
.stream-cursor {
  display: inline-block;
  color: var(--el-color-primary);
  animation: blink 0.9s steps(1) infinite;
  margin-left: 2px;
}
@keyframes blink { 50% { opacity: 0; } }
.sources-block { margin-top: 16px; }
.sources-block h4 { margin: 0 0 8px; color: #606266; }
.source-item {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.source-item:hover { background: #f5f7fa; border-color: var(--el-color-primary-light-5); }
.source-index {
  flex: none;
  font-weight: 600;
  color: var(--el-color-primary);
  font-family: monospace;
}
.source-body { min-width: 0; }
.source-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.source-title-text {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-score { flex: none; }
.source-snippet {
  color: #606266;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
