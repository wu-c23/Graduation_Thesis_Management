import axios from 'axios'
import { clearSession, getToken } from '../auth/session'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

const http = axios.create({
  baseURL: '/api',
  timeout: 5000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const status = error.response?.status
    let message = error.response?.data?.message
    // 下载接口以 Blob 接收，出错时需要先读出 JSON 才能拿到后端提示。
    if (!message && error.response?.data instanceof Blob) {
      try {
        const body = JSON.parse(await error.response.data.text())
        message = body?.message
      } catch {
        message = ''
      }
    }
    message = message || error.message || '网络请求失败'
    const isLoginRequest = String(error.config?.url || '').includes('/auth/login')
    if (status === 401 && !isLoginRequest) {
      clearSession()
      if (unauthorizedHandler) {
        unauthorizedHandler(message)
      }
    }
    return Promise.reject(new Error(message))
  }
)

export default http
