import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import StudentWorkbenchView from '../views/StudentWorkbenchView.vue'
import AdvisorHomeView from '../views/AdvisorHomeView.vue'
import { getStoredUser, getToken, roleHome } from '../auth/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录', guestOnly: true }
    },
    {
      path: '/student',
      name: 'student',
      component: StudentWorkbenchView,
      meta: { title: '学生工作台', requiresAuth: true, role: 'STUDENT' }
    },
    {
      path: '/advisor',
      name: 'advisor',
      component: AdvisorHomeView,
      meta: { title: '导师工作台', requiresAuth: true, role: 'ADVISOR' }
    }
  ]
})

router.beforeEach((to) => {
  const token = getToken()
  const user = getStoredUser()

  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.role && user?.role && user.role !== to.meta.role) {
    return roleHome(user.role)
  }
  if (to.meta.guestOnly && token && user?.role) {
    return roleHome(user.role)
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '首页'} · 毕业论文过程管理平台`
})

export default router
