import axios from 'axios'
import { ElMessage } from 'element-plus'

export const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    const message = err.response?.data?.message || '请求失败，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)
