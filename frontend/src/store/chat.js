import { ref } from 'vue'

// 模块级状态：路由切换（如点击引用查看文档详情）不丢失会话；
// 流式连接挂在模块上，离开页面期间继续接收，返回时直接看到最新结果。
export const messages = ref([])
export const includeDraft = ref(false)
export const streaming = ref(false)

const MAX_HISTORY_TURNS = 5
let eventSource = null
let currentMessage = null

export function ask(questionText) {
  const text = (questionText ?? '').trim()
  if (!text || streaming.value) return
  closeStream()

  currentMessage = {
    question: text,
    answer: '',
    sources: [],
    streaming: true
  }
  messages.value.push(currentMessage)
  streaming.value = true

  const history = messages.value
    .slice(0, -1)
    .slice(-MAX_HISTORY_TURNS)
    .map((m) => ({ question: m.question, answer: m.answer }))
  const params = new URLSearchParams({
    question: text,
    includeDraft: includeDraft.value,
    history: JSON.stringify(history)
  })
  eventSource = new EventSource(`/api/chat?${params}`)
  eventSource.addEventListener('sources', (e) => {
    currentMessage.sources = JSON.parse(e.data)
  })
  eventSource.addEventListener('token', (e) => {
    currentMessage.answer += e.data
  })
  eventSource.addEventListener('done', () => finish())
  eventSource.onerror = () => finish()
}

function finish() {
  if (currentMessage) {
    currentMessage.streaming = false
  }
  streaming.value = false
  closeStream()
}

export function closeStream() {
  eventSource?.close()
  eventSource = null
}

export function clearConversation() {
  closeStream()
  messages.value = []
  streaming.value = false
}
