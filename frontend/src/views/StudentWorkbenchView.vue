<template>
  <div class="page-shell" v-loading="loading">
    <header class="page-header">
      <div>
        <h1>学生工作台</h1>
        <p class="subtitle" v-if="project">
          {{ project.student.realName }} · {{ project.title }} · 导师：{{ project.advisor.realName }}
        </p>
        <p class="subtitle" v-else>正在加载课题信息…</p>
      </div>
      <div class="header-actions">
        <el-tag type="success" effect="plain">{{ roleLabel(user?.role || 'STUDENT') }}</el-tag>
        <span class="user-name">{{ user?.realName || project?.student.realName || '' }}</span>
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
      title="第 2 周已接入登录、角色隔离和真实六步进度；材料提交与设计评审将在第 3 周开放。"
    />

    <template v-if="project">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-title">
            六阶段进度
            <span class="muted">已通过 {{ project.completedStepCount }} / 6 步</span>
          </div>
        </template>
        <el-steps :active="project.completedStepCount" finish-status="success" align-center>
          <el-step
            v-for="step in project.steps"
            :key="step.key"
            :title="step.title"
            :description="step.description"
          />
        </el-steps>
        <div class="step-summary">
          <el-tag :type="currentTagType" effect="plain">
            {{ project.allCompleted ? '全部完成' : `当前阶段：${currentStageTitle}` }}
          </el-tag>
          <span class="muted">进度由材料与评审记录实时计算，第 3–4 周逐步开放提交入口。</span>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="材料提交" name="material">
            <el-table :data="materialRows" stripe>
              <el-table-column prop="title" label="材料类型" width="130" />
              <el-table-column prop="rule" label="提交要求" />
              <el-table-column prop="statusLabel" label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.tagType" effect="plain">{{ row.statusLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="说明" />
              <el-table-column label="操作" width="130">
                <template #default>
                  <el-button size="small" disabled>上传（W3）</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p class="tab-note">第 3 周实现文件上传、外链提交、退回重交与历史记录。</p>
          </el-tab-pane>

          <el-tab-pane label="设计评审" name="review">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(reviewStep?.status).tag" effect="plain">
                {{ stepStatusMeta(reviewStep?.status).label }}
              </el-tag>
              <span>{{ reviewStep?.description }}</span>
            </div>
            <el-empty description="设计评审提交与导师评分将在第 3 周开放" />
          </el-tab-pane>

          <el-tab-pane label="周报" name="weekly">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(weeklyStep?.status).tag" effect="plain">
                {{ stepStatusMeta(weeklyStep?.status).label }}
              </el-tag>
              <span>{{ weeklyStep?.description }}</span>
            </div>
            <el-empty description="周报提交与导师批阅将在第 4 周开放" />
          </el-tab-pane>

          <el-tab-pane label="代码仓库" name="repo">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(repoStep?.status).tag" effect="plain">
                {{ stepStatusMeta(repoStep?.status).label }}
              </el-tag>
              <span>{{ repoStep?.description }}</span>
            </div>
            <el-form label-position="top" class="repo-form">
              <el-form-item label="GitHub / Gitee 公开仓库地址">
                <el-input placeholder="https://github.com/your-name/your-project" disabled />
              </el-form-item>
              <el-button disabled>绑定并同步（W4）</el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="测试报告" name="test">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(testStep?.status).tag" effect="plain">
                {{ stepStatusMeta(testStep?.status).label }}
              </el-tag>
              <span>{{ testStep?.description }}</span>
            </div>
            <el-empty description="测试报告提交与导师审核将在第 4–5 周开放" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <el-empty v-else-if="!loading && !loadError" description="未加载到课题信息" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { clearSession, getStoredUser, roleLabel } from '../auth/session'
import { stepStatusMeta } from '../utils/status'

const router = useRouter()
const user = getStoredUser()
const project = ref(null)
const loading = ref(false)
const loadError = ref('')
const activeTab = ref('material')

const MATERIAL_RULES = {
  REQUIREMENT: 'PDF / DOCX / ZIP，或外部文档链接',
  PROTOTYPE: '图片 / ZIP，或 Axure、Figma、墨刀链接'
}

const materialRows = computed(() => {
  return (project.value?.steps || [])
    .filter((step) => MATERIAL_RULES[step.key])
    .map((step) => {
      const meta = stepStatusMeta(step.status)
      return {
        ...step,
        rule: MATERIAL_RULES[step.key],
        statusLabel: meta.label,
        tagType: meta.tag
      }
    })
})

function stepOf(key) {
  return project.value?.steps?.find((step) => step.key === key)
}

const reviewStep = computed(() => stepOf('DESIGN_REVIEW'))
const weeklyStep = computed(() => stepOf('WEEKLY_REPORT'))
const repoStep = computed(() => stepOf('REPO_BINDING'))
const testStep = computed(() => stepOf('TEST_REPORT'))

const currentStepInfo = computed(() => {
  if (!project.value) return null
  return project.value.steps[project.value.currentStep - 1] || null
})

const currentStageTitle = computed(() => currentStepInfo.value?.title || '—')

const currentTagType = computed(() => {
  if (project.value?.allCompleted) return 'success'
  return stepStatusMeta(currentStepInfo.value?.status).tag
})

async function loadProject() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await http.get('/student/project')
    project.value = response.data
  } catch (error) {
    loadError.value = error.message || '课题信息加载失败'
  } finally {
    loading.value = false
  }
}

function handleLogout() {
  clearSession()
  router.replace('/login')
}

onMounted(loadProject)
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

.card-title {
  font-weight: 650;
}

.card-title .muted {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
}

.step-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
  font-size: 12px;
}

.tab-note {
  margin: 14px 0 0;
  color: #8a97a8;
  font-size: 12px;
}

.tab-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: #5b6b81;
  font-size: 13px;
}

.repo-form {
  max-width: 560px;
}
</style>
