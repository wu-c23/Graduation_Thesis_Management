export const STEP_STATUS_META = {
  COMPLETED: { label: '已通过', tag: 'success' },
  IN_REVIEW: { label: '审核中', tag: 'warning' },
  RETURNED: { label: '已退回', tag: 'danger' },
  NOT_STARTED: { label: '未开始', tag: 'info' }
}

export function stepStatusMeta(status) {
  return STEP_STATUS_META[status] || STEP_STATUS_META.NOT_STARTED
}
