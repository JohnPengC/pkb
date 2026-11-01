import { http } from './client'

export function uploadFile(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/ingest/file', form)
}

export function submitUrl(url) {
  return http.post('/ingest/url', { url })
}

export function listTasks() {
  return http.get('/ingest/tasks').then((r) => r.data)
}

export function retryTask(id) {
  return http.post(`/ingest/tasks/${id}/retry`).then((r) => r.data)
}
