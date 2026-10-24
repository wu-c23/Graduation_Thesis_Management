<template>
  <div class="page-shell" v-loading="loading">
    <header class="page-header">
      <div>
        <h1>导师工作台</h1>
        <p class="subtitle">
          {{ user?.realName || '导师' }} · 名下学生进度与待处理事项
        </p>
      </div>
      <div class="header-actions">
        <el-tag type="warning" effect="plain">{{ roleLabel(user?.role || 'ADVISOR') }}</el-tag>
        <span class="user-name">{{ user?.realName || '' }}</span>
        <el-button @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="placeholder-banner"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    />
    <el-alert
      v-else
      class="placeholder-banner"
      type="info"
      :closable="false"
      show-icon
      title="第 2 周已接入登录、导师数据隔离与真实学生进度；材料审核将在第 3 周开放。"
    />

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">名下学生</div>
          <div class="stat-value">{{ summary?.studentCount ?? 0 }}</div>
          <div class="stat-hint">仅统计当前导师名下</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待审核材料</div>
          <div class="stat-value">{{ summary?.pendingArtifactCount ?? 0 }}</div>
          <div class="stat-hint">材料审核第 3 周开放</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待批阅周报</div>
          <div class="stat-value">{{ summary?.pendingWeeklyReportCount ?? 0 }}</div>
          <div class="stat-hint">周报批阅第 4 周开放</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-title">学生进度</div>
      </template>

      <el-table :data="students" stripe>
        <el-table-column prop="studentName" label="学生" width="110" />
        <el-table-column prop="username" label="学号" width="120" />
        <el-table-column prop="title" label="课题" />
        <el-table-column prop="currentStage" label="当前阶段" width="130">
          <template #default="{ row }">
            <el-tag :type="stageTagType(row.currentStepStatus)" effect="plain">
              {{ row.currentStage }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已通过步骤" width="110">
          <template #default="{ row }">{{ row.completedStepCount }} / 6</template>
        </el-table-column>
        <el-table-column prop="pendingCount" label="待处理" width="90" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && students.length === 0 && !loadError"
        description="名下暂无学生"
      />
      <p class="tab-note">导师只能查看自己名下的学生；材料审核与周报批阅将在第 3–4 周开放。</p>
    </el-card>

    <el-drawer v-model="drawerVisible" title="学生进度详情" size="520px">
      <div v-if="detailLoading" class="detail-loading" v-loading="true">
        正在加载学生详情…
      </div>
      <template v-else-if="detail">
        <div class="detail-head">
          <h3>{{ detail.student.realName }} · {{ detail.title }}</h3>
          <p class="muted">
            导师：{{ detail.advisor.realName }} · 已通过 {{ detail.completedStepCount }} / 6 步
          </p>
        </div>
        <el-steps direction="vertical" :active="detail.completedStepCount" finish-status="success">
          <el-step
            v-for="step in detail.steps"
            :key="step.key"
            :title="step.title"
            :description="step.description"
          />
        </el-steps>
      </template>
      <el-empty v-else description="暂无详情" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { clearSession, getStoredUser, roleLabel } from '../auth/session'
import { stepStatusMeta } from '../utils/status'

const router = useRouter()
const user = getStoredUser()
const summary = ref(null)
const loading = ref(false)
const loadError = ref('')

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const students = computed(() => summary.value?.students || [])

function stageTagType(status) {
  return stepStatusMeta(status).tag
}

async function loadStudents() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await http.get('/advisor/students')
    summary.value = response.data
  } catch (error) {
    loadError.value = error.message || '学生列表加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  drawerVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    const response = await http.get(`/advisor/students/${row.projectId}`)
    detail.value = response.data
  } catch (error) {
    ElMessage.error(error.message || '学生详情加载失败')
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function handleLogout() {
  clearSession()
  router.replace('/login')
}

onMounted(loadStudents)
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  color: #42526a;
  font-size: 13px;
}

.stats-row {
  margin-bottom: 18px;
}

.stat-card {
  border-radius: 10px;
}

.stat-label {
  color: #6b7a90;
  font-size: 13px;
}

.stat-value {
  margin: 8px 0 4px;
  font-size: 30px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-hint {
  color: #9aa6b6;
  font-size: 12px;
}

.card-title {
  font-weight: 650;
}

.tab-note {
  margin: 14px 0 0;
  color: #8a97a8;
  font-size: 12px;
}

.detail-loading {
  min-height: 180px;
}

.detail-head h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.detail-head p {
  margin: 0 0 22px;
  font-size: 13px;
}
</style>
