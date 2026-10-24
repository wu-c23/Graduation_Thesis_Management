import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { clearSession } from './auth/session'
import './styles.css'

setUnauthorizedHandler((message) => {
  clearSession()
  if (router.currentRoute.value.path !== '/login') {
    ElMessage.warning(message || '登录已过期，请重新登录')
    router.replace('/login')
  }
})

createApp(App).use(ElementPlus).use(router).mount('#app')
