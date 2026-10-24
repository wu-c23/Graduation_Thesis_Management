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
      title="第 4 周已开放周报批阅、代码仓库自动同步与手工兜底、测试报告提交审核；六步进度全部由真实数据计算。"
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
          <span class="muted">进度由材料与评审记录实时计算，重新提交材料后对应步骤会回到审核中。</span>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="材料提交" name="material">
            <el-table :data="materialRows" stripe>
              <el-table-column label="材料类型" width="150">
                <template #default="{ row }">
                  <div class="material-name">{{ row.label }}</div>
                  <div class="material-rule">{{ row.rule }}</div>
                </template>
              </el-table-column>
              <el-table-column label="最新提交" min-width="210">
                <template #default="{ row }">
                  <template v-if="row.latest">
                    <a
                      v-if="row.latest.externalUrl"
                      class="link-text"
                      :href="row.latest.externalUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      :title="row.latest.externalUrl"
                    >{{ row.latest.externalUrl }}</a>
                    <span v-else>{{ row.latest.fileName }}</span>
                    <div class="muted small">{{ formatDateTime(row.latest.submittedAt) }}</div>
                    <div v-if="row.type === 'TEST_REPORT'" class="metric-line">
                      {{ testMetricLabel(row.latest) }}
                    </div>
                  </template>
                  <span v-else class="muted">尚未提交</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="96">
                <template #default="{ row }">
                  <el-tag :type="artifactStatusMeta(row.latest?.status).tag" effect="plain">
                    {{ artifactStatusMeta(row.latest?.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="导师反馈" min-width="150">
                <template #default="{ row }">
                  <span v-if="row.latest?.feedback">{{ row.latest.feedback }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="row.latest?.status === 'SUBMITTED'"
                    @click="openSubmit(row)"
                  >
                    {{ submitButtonLabel(row) }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="history-head">
              <span class="section-title">提交历史</span>
              <span class="muted">共 {{ artifacts.length }} 次提交，旧版本保留可追溯</span>
            </div>
            <el-table :data="artifacts" stripe size="small">
              <el-table-column prop="typeLabel" label="类型" width="90" />
              <el-table-column label="提交内容" min-width="200">
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
                </template>
              </el-table-column>
              <el-table-column label="状态" width="96">
                <template #default="{ row }">
                  <el-tag :type="artifactStatusMeta(row.status).tag" effect="plain">
                    {{ artifactStatusMeta(row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评分" width="70">
                <template #default="{ row }">{{ row.score ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="测试统计" width="165">
                <template #default="{ row }">
                  <span v-if="row.type === 'TEST_REPORT'">{{ testMetricLabel(row) }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="feedback" label="导师评语" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.feedback || '—' }}</template>
              </el-table-column>
              <el-table-column label="提交时间" width="150">
                <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button
                    v-if="row.downloadable"
                    link
                    type="primary"
                    @click="handleDownload(row)"
                  >下载</el-button>
                  <span v-else class="muted">外链</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="artifacts.length === 0"
              description="还没有提交过材料，点击上方「提交材料」开始"
              :image-size="72"
            />
          </el-tab-pane>

          <el-tab-pane label="设计评审" name="review">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(reviewStep?.status).tag" effect="plain">
                {{ stepStatusMeta(reviewStep?.status).label }}
              </el-tag>
              <span>{{ reviewStep?.description }}</span>
            </div>

            <el-alert
              v-if="!materialsReady"
              type="info"
              :closable="false"
              show-icon
              title="需求文档与原型均通过导师审核后，导师即可进行设计评审。"
            />
            <el-alert
              v-else-if="!designReviews.length"
              type="success"
              :closable="false"
              show-icon
              title="材料已全部通过，等待导师提交设计评审（0–100 分 + 通过/退回）。"
            />

            <el-table :data="designReviews" stripe class="review-table">
              <el-table-column prop="roundNo" label="轮次" width="70">
                <template #default="{ row }">第 {{ row.roundNo }} 轮</template>
              </el-table-column>
              <el-table-column label="结论" width="90">
                <template #default="{ row }">
                  <el-tag :type="designResultMeta(row.result).tag" effect="plain">
                    {{ designResultMeta(row.result).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评分" width="80">
                <template #default="{ row }">{{ row.score }} 分</template>
              </el-table-column>
              <el-table-column prop="comment" label="评语" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.comment || '—' }}</template>
              </el-table-column>
              <el-table-column prop="reviewerName" label="评审人" width="100" />
              <el-table-column label="评审时间" width="150">
                <template #default="{ row }">{{ formatDateTime(row.reviewedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="!designReviews.length"
              description="暂无设计评审记录"
              :image-size="72"
            />
          </el-tab-pane>

          <el-tab-pane label="周报" name="weekly">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(weeklyStep?.status).tag" effect="plain">
                {{ stepStatusMeta(weeklyStep?.status).label }}
              </el-tag>
              <span>{{ weeklyStep?.description }}</span>
              <el-button type="primary" size="small" class="status-action" @click="openWeekly()">
                提交周报
              </el-button>
            </div>
            <el-table :data="weeklyReports" stripe empty-text="还没有提交过周报">
              <el-table-column label="周次" width="80">
                <template #default="{ row }">第 {{ row.weekNo }} 周</template>
              </el-table-column>
              <el-table-column prop="content" label="本周进展" min-width="240" show-overflow-tooltip />
              <el-table-column label="状态" width="94">
                <template #default="{ row }">
                  <el-tag :type="weeklyStatusMeta(row.status).tag" effect="plain" size="small">
                    {{ weeklyStatusMeta(row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="导师批阅" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.comment || '—' }}</template>
              </el-table-column>
              <el-table-column label="提交时间" width="150">
                <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="96">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status === 'RETURNED'"
                    link
                    type="primary"
                    @click="openWeekly(row)"
                  >修改重交</el-button>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
            </el-table>
            <p class="tab-note">
              每周保存一条记录；退回后可修改重交，至少 1 篇经导师批阅后该步骤完成。
            </p>
          </el-tab-pane>

          <el-tab-pane label="代码仓库" name="repo">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(repoStep?.status).tag" effect="plain">
                {{ stepStatusMeta(repoStep?.status).label }}
              </el-tag>
              <span>{{ repoStep?.description }}</span>
            </div>

            <el-alert
              v-if="repoBinding?.syncStatus === 'FAILED'"
              type="warning"
              :closable="false"
              show-icon
              class="repo-alert"
              title="自动同步失败：请确认仓库是公开仓库，或直接使用下方「手工填写提交数」完成本周演示。"
            />

            <template v-if="repoBinding">
              <el-descriptions :column="3" border size="small" class="repo-info">
                <el-descriptions-item label="仓库">
                  <a
                    class="link-text"
                    :href="repoBinding.repoUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >{{ repoBinding.repoUrl }}</a>
                </el-descriptions-item>
                <el-descriptions-item label="平台">
                  {{ repoBinding.repoHost === 'GITEE' ? 'Gitee' : 'GitHub' }}
                </el-descriptions-item>
                <el-descriptions-item label="同步状态">
                  <el-tag :type="repoStatusMeta(repoBinding.syncStatus).tag" effect="plain" size="small">
                    {{ repoStatusMeta(repoBinding.syncStatus).label }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="提交数">
                  {{ repoBinding.commitCount ?? 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="最近提交">
                  {{ formatDateTime(repoBinding.latestCommitAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="最近同步">
                  {{ formatDateTime(repoBinding.lastSyncAt) }}
                </el-descriptions-item>
              </el-descriptions>

              <div class="repo-actions">
                <el-button :loading="repoSyncing" @click="syncRepo">重新同步</el-button>
                <el-button link type="primary" @click="repoForm.editing = !repoForm.editing">
                  {{ repoForm.editing ? '收起' : '修改绑定' }}
                </el-button>
              </div>

              <el-form v-if="repoForm.editing" label-position="top" class="repo-form">
                <el-form-item label="新的 GitHub / Gitee 公开仓库地址">
                  <el-input v-model="repoForm.url" placeholder="https://github.com/your-name/your-project" />
                </el-form-item>
                <el-button type="primary" :loading="repoBindingBusy" @click="bindRepo(true)">
                  保存并同步
                </el-button>
              </el-form>
            </template>

            <el-form v-else label-position="top" class="repo-form">
              <el-form-item label="GitHub / Gitee 公开仓库地址">
                <el-input
                  v-model="repoForm.url"
                  placeholder="https://github.com/your-name/your-project"
                />
              </el-form-item>
              <el-button type="primary" :loading="repoBindingBusy" @click="bindRepo(true)">
                绑定并同步
              </el-button>
            </el-form>

            <el-divider content-position="left">手工兜底</el-divider>
            <el-form v-if="repoBinding" :inline="true" class="manual-form">
              <el-form-item label="提交数">
                <el-input-number v-model="repoForm.commitCount" :min="0" :max="100000" />
              </el-form-item>
              <el-form-item label="最近提交时间（可选）">
                <el-date-picker
                  v-model="repoForm.latestCommitAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择时间"
                />
              </el-form-item>
              <el-form-item>
                <el-button :loading="manualSubmitting" @click="saveManualCount">
                  保存提交数
                </el-button>
              </el-form-item>
            </el-form>
            <p class="tab-note">
              自动同步读取公开仓库的提交数与最近提交时间；网络不可用时可用手工填写完成演示。
            </p>
          </el-tab-pane>

          <el-tab-pane label="测试报告" name="test">
            <div class="tab-status">
              <el-tag :type="stepStatusMeta(testStep?.status).tag" effect="plain">
                {{ stepStatusMeta(testStep?.status).label }}
              </el-tag>
              <span>{{ testStep?.description }}</span>
              <el-button
                type="primary"
                size="small"
                class="status-action"
                :disabled="latestTestReport?.status === 'SUBMITTED'"
                @click="openSubmit(testReportRow)"
              >
                {{ latestTestReport?.status === 'SUBMITTED' ? '等待审核' : '提交测试报告' }}
              </el-button>
            </div>

            <el-descriptions
              v-if="latestTestReport"
              :column="4"
              border
              size="small"
              class="repo-info"
            >
              <el-descriptions-item label="报告">
                <a
                  v-if="latestTestReport.externalUrl"
                  class="link-text"
                  :href="latestTestReport.externalUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ latestTestReport.externalUrl }}</a>
                <span v-else>{{ latestTestReport.fileName }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="用例数">
                {{ latestTestReport.testCaseCount ?? '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="通过率">
                {{ latestTestReport.testPassRate == null ? '—' : latestTestReport.testPassRate + '%' }}
              </el-descriptions-item>
              <el-descriptions-item label="缺陷数">
                {{ latestTestReport.defectCount ?? '—' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-table :data="testReports" stripe class="review-table" empty-text="还没有提交测试报告">
              <el-table-column label="报告" min-width="190">
                <template #default="{ row }">
                  <span>{{ row.fileName || row.externalUrl }}</span>
                  <div class="muted small">{{ formatDateTime(row.submittedAt) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="测试统计" width="190">
                <template #default="{ row }">{{ testMetricLabel(row) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="94">
                <template #default="{ row }">
                  <el-tag :type="artifactStatusMeta(row.status).tag" effect="plain" size="small">
                    {{ artifactStatusMeta(row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="导师反馈" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.feedback || '—' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button
                    v-if="row.downloadable"
                    link
                    type="primary"
                    @click="handleDownload(row)"
                  >下载</el-button>
                  <span v-else class="muted">外链</span>
                </template>
              </el-table-column>
            </el-table>
            <p class="tab-note">
              测试报告复用材料审核流程；最新一份报告通过后，第六步完成。
            </p>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <el-empty v-else-if="!loading && !loadError" description="未加载到课题信息" />

    <el-dialog
      v-model="submitDialog.visible"
      :title="`提交${submitDialog.label}`"
      width="520px"
      @closed="resetSubmitForm"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="dialog-alert"
        :title="submitDialog.rule"
      />
      <el-radio-group v-model="submitForm.mode" class="submit-mode">
        <el-radio-button value="file">上传文件</el-radio-button>
        <el-radio-button value="link">外部链接</el-radio-button>
      </el-radio-group>

      <el-upload
        v-if="submitForm.mode === 'file'"
        v-model:file-list="submitForm.fileList"
        class="upload-block"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".pdf,.docx,.zip,.png,.jpg,.jpeg"
        :on-exceed="handleExceed"
        :on-change="handleFileChange"
      >
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF / DOCX / ZIP / PNG / JPG，单文件不超过 20MB；提交后保留原始文件名。
          </div>
        </template>
      </el-upload>

      <el-input
        v-else
        v-model="submitForm.url"
        placeholder="https://www.figma.com/file/..."
        maxlength="500"
        show-word-limit
      />
      <p v-if="submitForm.mode === 'link'" class="tab-note">
        支持 Axure、Figma、墨刀、在线文档等外部链接，导师可直接打开查看。
      </p>

      <el-form
        v-if="submitDialog.type === 'TEST_REPORT'"
        label-position="top"
        class="metric-form"
      >
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="用例数">
              <el-input-number
                v-model="submitForm.testCaseCount"
                :min="0"
                :max="100000"
                controls-position="right"
                class="metric-input"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="通过率（%）">
              <el-input-number
                v-model="submitForm.testPassRate"
                :min="0"
                :max="100"
                controls-position="right"
                class="metric-input"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="缺陷数">
              <el-input-number
                v-model="submitForm.defectCount"
                :min="0"
                :max="100000"
                controls-position="right"
                class="metric-input"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <p class="tab-note metric-note">测试统计可选，用于导师快速评估测试覆盖情况。</p>
      </el-form>

      <template #footer>
        <el-button @click="submitDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitForm.submitting" @click="submitMaterial">
          提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="weeklyDialog.visible"
      :title="weeklyDialog.editing ? '修改并重交周报' : '提交周报'"
      width="560px"
      @closed="resetWeeklyForm"
    >
      <el-form label-position="top">
        <el-form-item label="周次" required>
          <el-input-number
            v-model="weeklyForm.weekNo"
            :min="1"
            :max="30"
            :disabled="weeklyDialog.editing"
          />
          <span class="muted weekly-week-hint">第 1–30 周均可，按周保留一条记录</span>
        </el-form-item>
        <el-form-item label="本周进展" required>
          <el-input
            v-model="weeklyForm.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="例如：完成需求评审，补充借阅流程异常分支；下周计划完成原型初稿。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="weeklyDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="weeklySubmitting" @click="submitWeekly">
          提交
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

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'zip', 'png', 'jpg', 'jpeg']

const MATERIAL_TYPES = [
  { type: 'REQUIREMENT', label: '需求文档', rule: 'PDF / DOCX / ZIP，或外部文档链接' },
  { type: 'PROTOTYPE', label: '原型', rule: '图片 / ZIP，或 Axure、Figma、墨刀链接' },
  { type: 'TEST_REPORT', label: '测试报告', rule: 'PDF / DOCX / ZIP，或测试报告链接；可附测试统计' }
]

const router = useRouter()
const user = getStoredUser()
const project = ref(null)
const loading = ref(false)
const loadError = ref('')
const activeTab = ref('material')

const submitDialog = reactive({ visible: false, type: '', label: '', rule: '' })
const submitForm = reactive({
  mode: 'file',
  fileList: [],
  url: '',
  testCaseCount: null,
  testPassRate: null,
  defectCount: null,
  submitting: false
})

const weeklyDialog = reactive({ visible: false, editing: false })
const weeklyForm = reactive({ weekNo: 1, content: '' })
const weeklySubmitting = ref(false)
const repoForm = reactive({
  editing: false,
  url: '',
  commitCount: 0,
  latestCommitAt: null
})
const repoBindingBusy = ref(false)
const repoSyncing = ref(false)
const manualSubmitting = ref(false)

const artifacts = computed(() => project.value?.artifacts || [])
const designReviews = computed(() => project.value?.designReviews || [])
const weeklyReports = computed(() => project.value?.weeklyReports || [])
const repoBinding = computed(() => project.value?.repoBinding || null)
const testReports = computed(() => artifacts.value.filter((item) => item.type === 'TEST_REPORT'))
const latestTestReport = computed(() => testReports.value[0] || null)
const testReportRow = computed(() => materialRows.value.find((row) => row.type === 'TEST_REPORT'))

const latestByType = computed(() => {
  const map = {}
  for (const artifact of artifacts.value) {
    if (!map[artifact.type]) {
      map[artifact.type] = artifact
    }
  }
  return map
})

const materialRows = computed(() => MATERIAL_TYPES.map((item) => ({
  ...item,
  latest: latestByType.value[item.type] || null
})))

const materialsReady = computed(() => {
  const requirement = latestByType.value.REQUIREMENT
  const prototype = latestByType.value.PROTOTYPE
  return requirement?.status === 'APPROVED' && prototype?.status === 'APPROVED'
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

function submitButtonLabel(row) {
  if (row.latest?.status === 'SUBMITTED') return '等待审核'
  return row.latest ? '重新提交' : '提交材料'
}

async function loadProject() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await http.get('/student/project')
    project.value = response.data
    const binding = response.data.repoBinding
    if (binding) {
      if (!repoForm.editing) {
        repoForm.url = binding.repoUrl
      }
      repoForm.commitCount = binding.commitCount ?? 0
      repoForm.latestCommitAt = binding.latestCommitAt || null
    } else {
      repoForm.url = ''
      repoForm.commitCount = 0
      repoForm.latestCommitAt = null
    }
  } catch (error) {
    loadError.value = error.message || '课题信息加载失败'
  } finally {
    loading.value = false
  }
}

function openSubmit(row) {
  resetSubmitForm()
  submitDialog.type = row.type
  submitDialog.label = row.label
  submitDialog.rule = row.rule
  submitDialog.visible = true
}

function resetSubmitForm() {
  submitForm.mode = 'file'
  submitForm.fileList = []
  submitForm.url = ''
  submitForm.testCaseCount = null
  submitForm.testPassRate = null
  submitForm.defectCount = null
  submitForm.submitting = false
}

function handleExceed() {
  ElMessage.warning('一次只能提交一个文件，请先移除已选择的文件')
}

function handleFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const dot = raw.name.lastIndexOf('.')
  const extension = dot < 0 ? '' : raw.name.slice(dot + 1).toLowerCase()
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    ElMessage.warning('仅支持 PDF、DOCX、ZIP、PNG、JPG 文件')
    submitForm.fileList = []
    return
  }
  if (raw.size > MAX_UPLOAD_BYTES) {
    ElMessage.warning('文件大小不能超过 20MB')
    submitForm.fileList = []
  }
}

async function submitMaterial() {
  const form = submitForm
  if (form.mode === 'file' && form.fileList.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  if (form.mode === 'link') {
    const url = form.url.trim()
    if (!url) {
      ElMessage.warning('请填写外部链接')
      return
    }
    if (!/^https?:\/\/\S+$/i.test(url)) {
      ElMessage.warning('外部链接必须以 http:// 或 https:// 开头')
      return
    }
  }

  const data = new FormData()
  data.append('type', submitDialog.type)
  if (form.mode === 'file') {
    data.append('file', form.fileList[0].raw)
  } else {
    data.append('externalUrl', form.url.trim())
  }
  if (submitDialog.type === 'TEST_REPORT') {
    const metrics = {
      testCaseCount: form.testCaseCount,
      testPassRate: form.testPassRate,
      defectCount: form.defectCount
    }
    for (const [key, value] of Object.entries(metrics)) {
      if (value !== null && value !== undefined) {
        data.append(key, value)
      }
    }
  }

  form.submitting = true
  try {
    await http.post('/student/artifacts', data, { timeout: 60000 })
    ElMessage.success('材料已提交，等待导师审核')
    submitDialog.visible = false
    activeTab.value = 'material'
    await loadProject()
  } catch (error) {
    ElMessage.error(error.message || '材料提交失败')
  } finally {
    form.submitting = false
  }
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
  return parts.length ? parts.join(' · ') : '未填写统计'
}

function openWeekly(row = null) {
  weeklyDialog.visible = true
  weeklyDialog.editing = Boolean(row)
  if (row) {
    weeklyForm.weekNo = row.weekNo
    weeklyForm.content = row.content
  } else {
    const maxWeek = weeklyReports.value.reduce(
      (max, report) => Math.max(max, report.weekNo || 0), 0
    )
    weeklyForm.weekNo = Math.min(30, Math.max(1, maxWeek + 1))
    weeklyForm.content = ''
  }
}

function resetWeeklyForm() {
  weeklyForm.weekNo = 1
  weeklyForm.content = ''
  weeklyDialog.editing = false
  weeklySubmitting.value = false
}

async function submitWeekly() {
  const content = weeklyForm.content.trim()
  if (!content) {
    ElMessage.warning('请填写本周进展')
    return
  }
  if (!weeklyForm.weekNo) {
    ElMessage.warning('请选择周次')
    return
  }

  weeklySubmitting.value = true
  try {
    await http.post('/student/weekly-reports', {
      weekNo: weeklyForm.weekNo,
      content
    })
    ElMessage.success(weeklyDialog.editing ? '周报已重新提交' : '周报已提交，等待导师批阅')
    weeklyDialog.visible = false
    await loadProject()
  } catch (error) {
    ElMessage.error(error.message || '周报提交失败')
  } finally {
    weeklySubmitting.value = false
  }
}

async function bindRepo(syncAfter = true) {
  const url = repoForm.url.trim()
  if (!url) {
    ElMessage.warning('请填写仓库地址')
    return
  }
  if (!/^https:\/\/(www\.)?(github|gitee)\.com\/[^/]+\/[^/]+\/?$/i.test(url)) {
    ElMessage.warning('请填写 https://github.com/用户名/仓库名 或 https://gitee.com/用户名/仓库名')
    return
  }

  repoBindingBusy.value = true
  try {
    await http.post('/student/repo-binding', { repoUrl: url })
    repoForm.editing = false
    if (syncAfter) {
      await runRepoSync()
    } else {
      ElMessage.success('仓库已绑定')
    }
    await loadProject()
  } catch (error) {
    ElMessage.error(error.message || '仓库绑定失败')
  } finally {
    repoBindingBusy.value = false
  }
}

async function syncRepo() {
  repoSyncing.value = true
  try {
    await runRepoSync()
    await loadProject()
  } catch (error) {
    ElMessage.error(error.message || '仓库同步失败')
  } finally {
    repoSyncing.value = false
  }
}

async function runRepoSync() {
  const response = await http.post('/student/repo-binding/sync', null, { timeout: 15000 })
  const binding = response.data
  if (binding?.syncStatus === 'SUCCESS') {
    ElMessage.success(`已同步 ${binding.commitCount} 次提交`)
  } else {
    ElMessage.warning('自动同步失败，请确认仓库公开，或使用手工填写提交数')
  }
}

async function saveManualCount() {
  if (repoForm.commitCount === null || repoForm.commitCount === undefined) {
    ElMessage.warning('请填写提交数')
    return
  }
  manualSubmitting.value = true
  try {
    await http.post('/student/repo-binding/manual-count', {
      commitCount: repoForm.commitCount,
      latestCommitAt: repoForm.latestCommitAt || null
    })
    ElMessage.success('提交数已保存')
    await loadProject()
  } catch (error) {
    ElMessage.error(error.message || '提交数保存失败')
  } finally {
    manualSubmitting.value = false
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

.material-name {
  font-weight: 600;
}

.material-rule {
  margin-top: 2px;
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.5;
}

.metric-line {
  margin-top: 2px;
  color: #5b6b81;
  font-size: 12px;
}

.small {
  font-size: 12px;
}

.link-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.history-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 22px 0 10px;
  font-size: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 650;
}

.tab-note {
  margin: 14px 0 0;
  color: #8a97a8;
  font-size: 12px;
}

.tab-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
  color: #5b6b81;
  font-size: 13px;
}

.status-action {
  margin-left: auto;
}

.repo-form {
  max-width: 620px;
}

.repo-alert {
  margin-bottom: 14px;
}

.repo-info {
  margin-bottom: 12px;
}

.repo-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.manual-form {
  margin-top: 4px;
}

.metric-form {
  margin-top: 16px;
  padding: 12px 14px 2px;
  border-radius: 8px;
  background: #f7f9fc;
}

.metric-input {
  width: 100%;
}

.metric-note {
  margin-top: 0;
}

.weekly-week-hint {
  margin-left: 10px;
  font-size: 12px;
}

.review-table {
  margin-top: 14px;
}

.dialog-alert {
  margin-bottom: 16px;
}

.submit-mode {
  margin-bottom: 16px;
}

.upload-block {
  width: 100%;
}
</style>
