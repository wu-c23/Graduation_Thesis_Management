export const STEP_STATUS_META = {
  COMPLETED: { label: '已通过', tag: 'success' },
  IN_REVIEW: { label: '审核中', tag: 'warning' },
  IN_PROGRESS: { label: '进行中', tag: 'warning' },
  RETURNED: { label: '已退回', tag: 'danger' },
  NOT_STARTED: { label: '未开始', tag: 'info' }
}

export const ARTIFACT_STATUS_META = {
  SUBMITTED: { label: '待审核', tag: 'warning' },
  APPROVED: { label: '已通过', tag: 'success' },
  RETURNED: { label: '已退回', tag: 'danger' },
  NOT_STARTED: { label: '未提交', tag: 'info' }
}

export const DESIGN_RESULT_META = {
  PASS: { label: '通过', tag: 'success' },
  RETURN: { label: '退回', tag: 'danger' }
}

export const WEEKLY_STATUS_META = {
  SUBMITTED: { label: '待批阅', tag: 'warning' },
  REVIEWED: { label: '已批阅', tag: 'success' },
  RETURNED: { label: '已退回', tag: 'danger' }
}

export const REPO_STATUS_META = {
  SUCCESS: { label: '同步成功', tag: 'success' },
  FAILED: { label: '同步失败', tag: 'danger' },
  NOT_SYNCED: { label: '未同步', tag: 'info' }
}

export function stepStatusMeta(status) {
  return STEP_STATUS_META[status] || STEP_STATUS_META.NOT_STARTED
}

export function artifactStatusMeta(status) {
  return ARTIFACT_STATUS_META[status] || ARTIFACT_STATUS_META.NOT_STARTED
}

export function designResultMeta(result) {
  return DESIGN_RESULT_META[result] || { label: result || '—', tag: 'info' }
}

export function weeklyStatusMeta(status) {
  return WEEKLY_STATUS_META[status] || { label: status || '—', tag: 'info' }
}

export function repoStatusMeta(status) {
  return REPO_STATUS_META[status] || { label: status || '未同步', tag: 'info' }
}

export function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
