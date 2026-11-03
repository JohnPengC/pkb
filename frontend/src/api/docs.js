import { http } from './client'

export function listDocs(params) {
  return http.get('/docs', { params }).then((r) => r.data)
}

export function getDoc(id) {
  return http.get(`/docs/${id}`).then((r) => r.data)
}

export function getDocSource(id) {
  return http.get(`/docs/${id}/source`).then((r) => r.data)
}

export function saveDoc(id, content, changeNote) {
  return http.put(`/docs/${id}`, { content, changeNote }).then((r) => r.data)
}

export function confirmDoc(id) {
  return http.post(`/docs/${id}/confirm`).then((r) => r.data)
}

export function listVersions(id) {
  return http.get(`/docs/${id}/versions`).then((r) => r.data)
}

export function diffVersions(id, from, to) {
  return http.get(`/docs/${id}/diff`, { params: { from, to } }).then((r) => r.data)
}

export function rollback(id, versionNo) {
  return http.post(`/docs/${id}/rollback/${versionNo}`).then((r) => r.data)
}

export function setTags(id, tags) {
  return http.put(`/docs/${id}/tags`, { tags }).then((r) => r.data)
}

export function allTags() {
  return http.get('/docs/tags').then((r) => r.data)
}

export function deleteDoc(id) {
  return http.delete(`/docs/${id}`)
}
