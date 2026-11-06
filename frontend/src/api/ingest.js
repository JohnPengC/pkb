import { http } from './client'

export function uploadFile(file, mode = 'DISTILL', opts = {}) {
  const form = new FormData()
  form.append('file', file)
  form.append('mode', mode)
  return http.post('/ingest/file', form, { silent: !!opts.silent })
}

export function submitUrl(url, mode = 'DISTILL') {
  return http.post('/ingest/url', { url, mode })
}

export function listTasks() {
  return http.get('/ingest/tasks').then((r) => r.data)
}

export function retryTask(id) {
  return http.post(`/ingest/tasks/${id}/retry`).then((r) => r.data)
}

export function deleteTask(id) {
  return http.delete(`/ingest/tasks/${id}`)
}

export function redistillTask(id, mode) {
  return http.post(`/ingest/tasks/${id}/redistill`, { mode }).then((r) => r.data)
}
