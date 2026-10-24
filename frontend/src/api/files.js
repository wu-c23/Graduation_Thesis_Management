import http from './http'

/**
 * 材料下载需要携带 JWT，不能直接用 <a href>，先取 Blob 再触发浏览器保存。
 */
export async function downloadArtifact(artifactId, fileName) {
  const blob = await http.get(`/files/${artifactId}`, {
    responseType: 'blob',
    timeout: 60000
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || '材料'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}
