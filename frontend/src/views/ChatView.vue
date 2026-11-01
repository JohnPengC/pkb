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
      <MdPreview :modelValue="answer" />
    </el-card>

    <div v-if="sources.length" style="margin-top: 12px">
      <h4>引用来源</h4>
      <el-card v-for="(s, i) in sources" :key="s.documentId" shadow="hover"
               style="margin-bottom: 8px; cursor: pointer" @click="$router.push(`/docs/${s.documentId}`)">
        <b>[{{ i + 1 }}] {{ s.title }}</b>
        <div style="color: #606266; font-size: 13px">{{ s.snippet }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

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
</style>
