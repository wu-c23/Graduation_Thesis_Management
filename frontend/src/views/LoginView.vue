<template>
  <div class="login-page">
    <div class="login-panel">
      <section class="intro">
        <div class="brand-badge">毕设 · 过程管理</div>
        <h1>把毕业设计的每一步<br />都留在系统里</h1>
        <p class="intro-text">
          需求文档、原型、设计评审、周报、代码仓库与测试报告，
          用一条主线串起学生与导师的协作过程。
        </p>
        <div class="flow-chips">
          <span v-for="item in flow" :key="item">{{ item }}</span>
        </div>
        <div class="scaffold-note">
          <strong>学生与导师统一入口</strong>
          <span>第 2 周已接入 JWT 登录与角色隔离，登录后按角色进入对应工作台。</span>
        </div>
      </section>

      <section class="login-card">
        <h2>账号登录</h2>
        <p class="muted">使用课程演示账号体验流程</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入学号 / 工号" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              size="large"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>

        <div class="demo-accounts">
          <span>演示账号</span>
          <code>stu001 / 123456</code>
          <code>tea001 / 123456</code>
        </div>

        <el-divider>开发联调</el-divider>

        <div class="health-row">
          <el-tag :type="healthTagType" effect="light">{{ healthLabel }}</el-tag>
          <span class="health-text">{{ health.message }}</span>
          <el-button text size="small" @click="checkHealth">重新检查</el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { roleHome, saveSession } from '../auth/session'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: 'stu001',
  password: '123456'
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const flow = ['需求文档', '原型', '设计评审', '周报', '代码仓库', '测试报告']

const health = reactive({
  status: 'checking',
  userCount: null,
  message: '正在检查后端连接...'
})

const healthTagType = computed(() => {
  if (health.status === 'up') return 'success'
  if (health.status === 'down') return 'danger'
  return 'info'
})

const healthLabel = computed(() => {
  if (health.status === 'up') return '后端正常'
  if (health.status === 'down') return '后端未连接'
  return '检查中'
})

async function checkHealth() {
  health.status = 'checking'
  health.message = '正在检查后端连接...'
  try {
    const response = await http.get('/health')
    if (response.code === 0) {
      health.status = 'up'
      health.userCount = response.data.userCount
      health.message = `后端与数据库正常，已加载 ${response.data.userCount} 个种子用户`
    } else {
      health.status = 'down'
      health.message = response.message || '后端返回异常'
    }
  } catch (error) {
    health.status = 'down'
    health.message = '后端未启动或无法连接（请先运行 backend）'
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const response = await http.post('/auth/login', {
      username: form.username.trim(),
      password: form.password
    })
    if (response.code !== 0) {
      throw new Error(response.message || '登录失败')
    }
    saveSession(response.data.token, response.data.user)
    ElMessage.success(`欢迎回来，${response.data.user.realName}`)
    await router.replace(roleHome(response.data.user.role))
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

onMounted(checkHealth)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 12% 18%, rgba(47, 111, 237, 0.16), transparent 32%),
    radial-gradient(circle at 88% 82%, rgba(23, 58, 117, 0.12), transparent 34%),
    #eef2f9;
}

.login-panel {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  border-radius: 24px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 28px 80px rgba(24, 45, 84, 0.18);
}

.intro {
  padding: 56px 52px;
  color: #fff;
  background: linear-gradient(145deg, #173a75 0%, #2f6fed 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 24px;
}

.brand-badge {
  align-self: flex-start;
  padding: 7px 12px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.intro h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.35;
  font-weight: 750;
}

.intro-text {
  margin: 0;
  max-width: 430px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  line-height: 1.9;
}

.flow-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.flow-chips span {
  padding: 7px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
}

.scaffold-note {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(7, 24, 54, 0.24);
  font-size: 12px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.78);
}

.scaffold-note strong {
  color: #fff;
}

.login-card {
  padding: 48px 44px 40px;
}

.login-card h2 {
  margin: 0 0 6px;
  font-size: 26px;
}

.login-card > .muted {
  margin: 0 0 28px;
  font-size: 13px;
}

.login-button {
  width: 100%;
  margin-top: 6px;
}

.demo-accounts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
  padding: 12px;
  border-radius: 10px;
  background: #f5f7fb;
  color: #6b7a90;
  font-size: 12px;
}

.demo-accounts code {
  padding: 3px 7px;
  border-radius: 5px;
  background: #e8eefb;
  color: #2f6fed;
}

.health-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.health-text {
  flex: 1;
  min-width: 150px;
  color: #6b7a90;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 860px) {
  .login-page {
    padding: 16px;
  }

  .login-panel {
    grid-template-columns: 1fr;
  }

  .intro {
    padding: 36px 28px;
  }

  .intro h1 {
    font-size: 27px;
  }

  .login-card {
    padding: 32px 24px;
  }
}
</style>
