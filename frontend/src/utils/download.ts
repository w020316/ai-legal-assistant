/**
 * 文件下载工具
 * <p>
 * v1.11.0 新增：统一处理 Blob 下载与文件名编码。
 */

/**
 * 触发浏览器下载 Blob 数据
 * @param blob 二进制数据
 * @param filename 文件名（含扩展名）
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 延迟释放，避免 Safari 下载失败
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * 生成带时间戳的安全文件名
 * @param prefix 文件名前缀（如 "linzAI会话导出"）
 * @param ext 扩展名（如 "docx" / "pdf" / "md"，不含点）
 * @returns 形如 "linzAI会话导出_2026-07-25.docx"
 */
export function buildFilename(prefix: string, ext: string): string {
  const date = new Date().toISOString().slice(0, 10)
  return `${prefix}_${date}.${ext}`
}
