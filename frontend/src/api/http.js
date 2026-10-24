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
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络请求失败'
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
