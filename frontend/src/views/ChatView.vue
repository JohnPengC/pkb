<template>
  <div class="chat-page">
    <h2>知识库问答</h2>

    <div v-if="messages.length" class="message-list">
      <div v-for="(m, i) in messages" :key="i" class="message-item">
        <div class="question">
          <el-icon class="q-icon"><User /></el-icon>
          <span>{{ m.question }}</span>
        </div>
        <el-card shadow="never" class="answer-card">
          <template v-if="m.streaming && !m.answer">
            <el-skeleton :rows="3" animated />
            <div class="waiting-hint">
              <el-icon class="is-loading"><Loading /></el-icon>
              正在检索资料并生成回答…
            </div>
          </template>
          <template v-else>
            <MdPreview :modelValue="m.answer" />
            <span v-if="m.streaming" class="stream-cursor">▍</span>
          </template>
        </el-card>

        <div v-if="m.sources.length" class="sources-block">
          <h4>引用来源（{{ m.sources.length }}）</h4>
          <div v-for="(s, j) in m.sources" :key="j" class="source-item"
               @click="$router.push({ path: `/docs/${s.documentId}`, query: { q: s.evidence || '' } })">
            <span class="source-index">[{{ j + 1 }}]</span>
            <div class="source-body">
              <div class="source-title">
                <span class="source-title-text">{{ s.title }}</span>
                <el-tag size="small" effect="plain" class="source-score">
                  相关度 {{ Math.round((s.score || 0) * 100) }}%
                </el-tag>
              </div>
              <div v-if="s.evidence" class="source-evidence">“{{ s.evidence }}”</div>
              <div class="source-snippet">{{ s.snippet }}</div>
            </div>
          </div>
        </div>
      </div>
      <el-button v-if="!streaming" text type="info" size="small" @click="clearConversation">
        清空对话
      </el-button>
    </div>

    <el-input v-model="draft" type="textarea" :rows="2" placeholder="向你的知识库提问…"
              @keydown.enter.exact.prevent="submit" />
    <el-space style="margin: 8px 0">
      <el-button type="primary" :loading="streaming" :disabled="!draft.trim()" @click="submit">
        提问（Enter）
      </el-button>
      <el-checkbox v-model="includeDraft">包含未确认草稿</el-checkbox>
    </el-space>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { Loading, User } from '@element-plus/icons-vue'
import { ask, clearConversation, includeDraft, messages, streaming } from '../store/chat'

const draft = ref('')

function submit() {
  const text = draft.value
  draft.value = ''
  ask(text)
}
</script>

<style scoped>
.chat-page { max-width: 860px; }
.message-list { margin-bottom: 16px; }
.message-item { margin-bottom: 24px; }
.question {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}
.q-icon { margin-top: 3px; color: var(--el-color-primary); }
.answer-card { margin-bottom: 4px; }
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
.sources-block { margin-top: 12px; }
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
.source-evidence {
  color: #b88230;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
  margin-bottom: 4px;
}
.source-snippet {
  color: #606266;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
