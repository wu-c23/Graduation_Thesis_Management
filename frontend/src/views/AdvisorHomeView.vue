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
      title="第 4 周已开放周报批阅与测试报告审核；学生绑定公开仓库后可自动同步提交统计。"
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
          <div class="stat-hint">点击学生详情即可审核</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待批阅周报</div>
          <div class="stat-value">{{ summary?.pendingWeeklyReportCount ?? 0 }}</div>
          <div class="stat-hint">在周报批阅区直接处理</div>
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
        <el-table-column label="待处理" width="90">
          <template #default="{ row }">
            <el-badge v-if="row.pendingCount > 0" :value="row.pendingCount" type="warning" />
            <span v-else class="muted">0</span>
          </template>
        </el-table-column>
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
      <p class="tab-note">导师只能查看自己名下的学生；材料、周报与设计评审操作均会校验课题归属。</p>
    </el-card>

    <el-drawer v-model="drawerVisible" title="学生进度详情" size="760px">
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

        <el-divider content-position="left">材料审核</el-divider>
        <el-table :data="detail.artifacts" size="small" stripe>
          <el-table-column prop="typeLabel" label="类型" width="86" />
          <el-table-column label="提交内容" min-width="150">
            <template #default="{ row }">
              <a
                v-if="row.externalUrl"
                class="link-text"
                :href="row.externalUrl"
                target="_blank"
                rel="noopener noreferrer"
                :title="row.externalUrl"
              >{{ row.externalUrl }}</a>
              <span v-else>{{ row.fileName || '—' }}</span>
              <div class="muted small">{{ formatDateTime(row.submittedAt) }}</div>
              <div v-if="row.type === 'TEST_REPORT'" class="metric-line">
                {{ testMetricLabel(row) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="审核结果" width="140">
            <template #default="{ row }">
              <el-tag :type="artifactStatusMeta(row.status).tag" effect="plain" size="small">
                {{ artifactStatusMeta(row.status).label }}
              </el-tag>
              <div v-if="row.score != null" class="small">{{ row.score }} 分</div>
              <div v-if="row.feedback" class="feedback-text">{{ row.feedback }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  v-if="row.downloadable"
                  link
                  type="primary"
                  @click="handleDownload(row)"
                >下载</el-button>
                <template v-if="row.status === 'SUBMITTED'">
                  <el-button link type="success" @click="openArtifactReview(row, 'APPROVED')">
                    通过
                  </el-button>
                  <el-button link type="danger" @click="openArtifactReview(row, 'RETURNED')">
                    退回
                  </el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!detail.artifacts?.length"
          description="学生尚未提交材料"
          :image-size="68"
        />

        <el-divider content-position="left">设计评审</el-divider>
        <el-alert
          v-if="!materialsApproved"
          type="info"
          :closable="false"
          show-icon
          title="需求文档与原型的最新版本均审核通过后，才能提交设计评审。"
          class="design-alert"
        />
        <el-form v-else :model="designForm" label-position="top" class="design-form">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="10">
              <el-form-item label="评分（0–100）">
                <el-input-number
                  v-model="designForm.score"
                  :min="0"
                  :max="100"
                  :step="1"
                  class="score-input"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="14">
              <el-form-item label="评审结论">
                <el-radio-group v-model="designForm.result">
                  <el-radio-button value="PASS">通过</el-radio-button>
                  <el-radio-button value="RETURN">退回</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="评审意见">
            <el-input
              v-model="designForm.comment"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              placeholder="填写设计评审意见（可选）"
            />
          </el-form-item>
          <el-button type="primary" :loading="designSubmitting" @click="submitDesignReview">
            提交设计评审
          </el-button>
        </el-form>

        <el-table :data="detail.designReviews" size="small" stripe class="review-table">
          <el-table-column label="轮次" width="70">
            <template #default="{ row }">第 {{ row.roundNo }} 轮</template>
          </el-table-column>
          <el-table-column label="结论" width="86">
            <template #default="{ row }">
              <el-tag :type="designResultMeta(row.result).tag" effect="plain" size="small">
                {{ designResultMeta(row.result).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="66">
            <template #default="{ row }">{{ row.score }} 分</template>
          </el-table-column>
          <el-table-column prop="comment" label="评语" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.comment || '—' }}</template>
          </el-table-column>
          <el-table-column label="评审时间" width="145">
            <template #default="{ row }">{{ formatDateTime(row.reviewedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!detail.designReviews?.length"
          description="暂无设计评审记录"
          :image-size="68"
        />

        <el-divider content-position="left">周报批阅</el-divider>
        <el-table :data="detail.weeklyReports" size="small" stripe>
          <el-table-column label="周次" width="70">
            <template #default="{ row }">第 {{ row.weekNo }} 周</template>
          </el-table-column>
          <el-table-column prop="content" label="本周进展" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <el-tag :type="weeklyStatusMeta(row.status).tag" effect="plain" size="small">
                {{ weeklyStatusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="批阅意见" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.comment || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="118">
            <template #default="{ row }">
              <div v-if="row.status === 'SUBMITTED'" class="row-actions">
                <el-button link type="success" @click="openWeeklyReview(row, 'REVIEWED')">
                  批阅
                </el-button>
                <el-button link type="danger" @click="openWeeklyReview(row, 'RETURNED')">
                  退回
                </el-button>
              </div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!detail.weeklyReports?.length"
          description="学生尚未提交周报"
          :image-size="68"
        />

        <el-divider content-position="left">代码仓库</el-divider>
        <el-descriptions
          v-if="detail.repoBinding"
          :column="2"
          border
          size="small"
          class="repo-info"
        >
          <el-descriptions-item label="仓库地址">
            <a
              class="link-text"
              :href="detail.repoBinding.repoUrl"
              target="_blank"
              rel="noopener noreferrer"
            >{{ detail.repoBinding.repoUrl }}</a>
          </el-descriptions-item>
          <el-descriptions-item label="平台">
            {{ detail.repoBinding.repoHost === 'GITEE' ? 'Gitee' : 'GitHub' }}
          </el-descriptions-item>
          <el-descriptions-item label="提交数">
            {{ detail.repoBinding.commitCount ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="同步状态">
            <el-tag :type="repoStatusMeta(detail.repoBinding.syncStatus).tag" effect="plain" size="small">
              {{ repoStatusMeta(detail.repoBinding.syncStatus).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最近提交">
            {{ formatDateTime(detail.repoBinding.latestCommitAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="最近同步">
            {{ formatDateTime(detail.repoBinding.lastSyncAt) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty
          v-else
          description="学生尚未绑定代码仓库"
          :image-size="68"
        />
      </template>
      <el-empty v-else description="暂无详情" />
    </el-drawer>

    <el-dialog
      v-model="artifactDialog.visible"
      :title="artifactDialog.result === 'RETURNED' ? '退回材料' : '通过材料'"
      width="460px"
      @closed="resetArtifactForm"
    >
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="材料类型">
          {{ artifactDialog.row?.typeLabel || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="提交内容">
          {{ artifactSourceLabel(artifactDialog.row) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="review-form">
        <el-form-item label="评分（可选，0–100）">
          <el-input-number
            v-model="artifactForm.score"
            :min="0"
            :max="100"
            :step="1"
            class="score-input"
          />
        </el-form-item>
        <el-form-item
          :label="artifactDialog.result === 'RETURNED' ? '退回评语（必填）' : '评语（可选）'"
          :required="artifactDialog.result === 'RETURNED'"
        >
          <el-input
            v-model="artifactForm.feedback"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :placeholder="artifactDialog.result === 'RETURNED' ? '请说明需要修改的内容' : '可填写审核意见'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="artifactDialog.visible = false">取消</el-button>
        <el-button
          :type="artifactDialog.result === 'RETURNED' ? 'danger' : 'success'"
          :loading="artifactSubmitting"
          @click="submitArtifactReview"
        >
          确认{{ artifactDialog.result === 'RETURNED' ? '退回' : '通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="weeklyDialog.visible"
      :title="weeklyDialog.status === 'RETURNED' ? '退回周报' : '批阅周报'"
      width="480px"
      @closed="resetWeeklyReview"
    >
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="周次">
          第 {{ weeklyDialog.row?.weekNo }} 周
        </el-descriptions-item>
        <el-descriptions-item label="本周进展">
          {{ weeklyDialog.row?.content || '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="review-form">
        <el-form-item
          :label="weeklyDialog.status === 'RETURNED' ? '退回意见（必填）' : '批阅意见（可选）'"
          :required="weeklyDialog.status === 'RETURNED'"
        >
          <el-input
            v-model="weeklyForm.comment"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :placeholder="weeklyDialog.status === 'RETURNED' ? '请说明需要补充的内容' : '可填写本周反馈'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="weeklyDialog.visible = false">取消</el-button>
        <el-button
          :type="weeklyDialog.status === 'RETURNED' ? 'danger' : 'success'"
          :loading="weeklySubmitting"
          @click="submitWeeklyReview"
        >
          确认{{ weeklyDialog.status === 'RETURNED' ? '退回' : '批阅' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { downloadArtifact } from '../api/files'
import { clearSession, getStoredUser, roleLabel } from '../auth/session'
import {
  artifactStatusMeta,
  designResultMeta,
  formatDateTime,
  repoStatusMeta,
  stepStatusMeta,
  weeklyStatusMeta
} from '../utils/status'

const router = useRouter()
const user = getStoredUser()
const summary = ref(null)
const loading = ref(false)
const loadError = ref('')

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const currentProjectId = ref(null)

const artifactDialog = reactive({ visible: false, row: null, result: 'APPROVED' })
const artifactForm = reactive({ score: 90, feedback: '' })
const artifactSubmitting = ref(false)

const designForm = reactive({ score: 90, result: 'PASS', comment: '' })
const designSubmitting = ref(false)

const weeklyDialog = reactive({ visible: false, row: null, status: 'REVIEWED' })
const weeklyForm = reactive({ comment: '' })
const weeklySubmitting = ref(false)

const students = computed(() => summary.value?.students || [])

const materialsApproved = computed(() => {
  const artifacts = detail.value?.artifacts || []
  const latestOf = (type) => artifacts.find((artifact) => artifact.type === type)
  return latestOf('REQUIREMENT')?.status === 'APPROVED'
    && latestOf('PROTOTYPE')?.status === 'APPROVED'
})

function stageTagType(status) {
  return stepStatusMeta(status).tag
}

function artifactSourceLabel(row) {
  if (!row) return '—'
  return row.fileName || row.externalUrl || '—'
}

function testMetricLabel(row) {
  if (!row) return '—'
  const parts = []
  if (row.testCaseCount !== null && row.testCaseCount !== undefined) {
    parts.push(`用例 ${row.testCaseCount}`)
  }
  if (row.testPassRate !== null && row.testPassRate !== undefined) {
    parts.push(`通过率 ${row.testPassRate}%`)
  }
  if (row.defectCount !== null && row.defectCount !== undefined) {
    parts.push(`缺陷 ${row.defectCount}`)
  }
  return parts.length ? parts.join(' · ') : '未填写测试统计'
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
  currentProjectId.value = row.projectId
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

async function refreshDetail() {
  if (!currentProjectId.value) return
  detailLoading.value = true
  try {
    const response = await http.get(`/advisor/students/${currentProjectId.value}`)
    detail.value = response.data
  } catch (error) {
    ElMessage.error(error.message || '学生详情刷新失败')
  } finally {
    detailLoading.value = false
  }
}

function openArtifactReview(row, result) {
  artifactDialog.row = row
  artifactDialog.result = result
  artifactForm.score = 90
  artifactForm.feedback = ''
  artifactDialog.visible = true
}

function resetArtifactForm() {
  artifactDialog.row = null
  artifactForm.score = 90
  artifactForm.feedback = ''
  artifactSubmitting.value = false
}

async function submitArtifactReview() {
  const row = artifactDialog.row
  if (!row) return
  const feedback = artifactForm.feedback.trim()
  if (artifactDialog.result === 'RETURNED' && !feedback) {
    ElMessage.warning('退回时必须填写评语')
    return
  }

  artifactSubmitting.value = true
  try {
    const body = { status: artifactDialog.result, feedback }
    if (artifactForm.score !== null && artifactForm.score !== undefined) {
      body.score = artifactForm.score
    }
    await http.post(`/advisor/artifacts/${row.id}/review`, body)
    ElMessage.success(artifactDialog.result === 'RETURNED' ? '材料已退回' : '材料已通过')
    artifactDialog.visible = false
    await Promise.all([refreshDetail(), loadStudents()])
  } catch (error) {
    ElMessage.error(error.message || '材料审核失败')
  } finally {
    artifactSubmitting.value = false
  }
}

async function submitDesignReview() {
  if (!currentProjectId.value) return
  if (designForm.score === null || designForm.score === undefined) {
    ElMessage.warning('请填写设计评审评分')
    return
  }

  designSubmitting.value = true
  try {
    await http.post(`/advisor/projects/${currentProjectId.value}/design-review`, {
      score: designForm.score,
      result: designForm.result,
      comment: designForm.comment.trim()
    })
    ElMessage.success(designForm.result === 'PASS' ? '设计评审已通过' : '设计评审已退回')
    designForm.comment = ''
    await Promise.all([refreshDetail(), loadStudents()])
  } catch (error) {
    ElMessage.error(error.message || '设计评审提交失败')
  } finally {
    designSubmitting.value = false
  }
}

function openWeeklyReview(row, status) {
  weeklyDialog.row = row
  weeklyDialog.status = status
  weeklyForm.comment = ''
  weeklyDialog.visible = true
}

function resetWeeklyReview() {
  weeklyDialog.row = null
  weeklyDialog.status = 'REVIEWED'
  weeklyForm.comment = ''
  weeklySubmitting.value = false
}

async function submitWeeklyReview() {
  const row = weeklyDialog.row
  if (!row) return
  const comment = weeklyForm.comment.trim()
  if (weeklyDialog.status === 'RETURNED' && !comment) {
    ElMessage.warning('退回周报时必须填写批阅意见')
    return
  }

  weeklySubmitting.value = true
  try {
    await http.post(`/advisor/weekly-reports/${row.id}/review`, {
      status: weeklyDialog.status,
      comment
    })
    ElMessage.success(weeklyDialog.status === 'RETURNED' ? '周报已退回' : '周报已批阅')
    weeklyDialog.visible = false
    await Promise.all([refreshDetail(), loadStudents()])
  } catch (error) {
    ElMessage.error(error.message || '周报批阅失败')
  } finally {
    weeklySubmitting.value = false
  }
}

async function handleDownload(row) {
  try {
    await downloadArtifact(row.id, row.fileName)
  } catch (error) {
    ElMessage.error(error.message || '文件下载失败')
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

.link-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.small {
  font-size: 12px;
}

.feedback-text {
  margin-top: 2px;
  color: #8a5a00;
  font-size: 12px;
  line-height: 1.4;
}

.metric-line {
  margin-top: 2px;
  color: #5b6b81;
  font-size: 12px;
}

.repo-info {
  margin-top: 4px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.design-alert {
  margin-bottom: 14px;
}

.design-form {
  padding: 14px 16px 4px;
  margin-bottom: 12px;
  background: #f7f9fc;
  border-radius: 8px;
}

.score-input {
  width: 140px;
}

.review-table {
  margin-top: 12px;
}

.review-form {
  margin-top: 16px;
}
</style>
