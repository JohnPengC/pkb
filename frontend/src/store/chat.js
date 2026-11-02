import { ref } from 'vue'

// 模块级状态：路由切换（如点击引用查看文档详情）不丢失问答内容；
// 流式连接挂在模块上，离开页面期间继续接收，返回时直接看到最新结果。
export const question = ref('')
export const answer = ref('')
export const sources = ref([])
export const includeDraft = ref(false)
export const streaming = ref(false)

let eventSource = null

export function ask() {
  if (!question.value.trim() || streaming.value) return
  closeStream()
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
    closeStream()
  })
  eventSource.onerror = () => {
    streaming.value = false
    closeStream()
  }
}

export function closeStream() {
  eventSource?.close()
  eventSource = null
}
