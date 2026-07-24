import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { CaseVO } from '@/api'

/**
 * 案例收藏夹（v1.10.0 新增）
 * <p>
 * 使用 localStorage 持久化用户收藏的案例，支持：
 * - 收藏 / 取消收藏
 * - 判断是否已收藏
 * - 导出收藏列表为 Markdown
 * - 清空收藏
 * <p>
 * 设计取舍：案例库为只读数据，无需后端持久化，前端 localStorage 即可满足需求。
 */

const STORAGE_KEY = 'linzai:caseFavorites'

// 全局共享的收藏列表（同一应用实例内多组件复用）
const favorites = ref<CaseVO[]>(loadFromStorage())

function loadFromStorage(): CaseVO[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(favorites.value))
  } catch (e) {
    // 配额超限或无痕模式，给出提示但不算失败
    console.warn('案例收藏持久化失败:', e)
  }
}

// 自动持久化
watch(favorites, persist, { deep: true })

export function useCaseFavorites() {
  // 判断是否已收藏
  function isFavorited(id: number): boolean {
    return favorites.value.some((c) => c.id === id)
  }

  // 切换收藏状态
  function toggleFavorite(c: CaseVO): boolean {
    const idx = favorites.value.findIndex((x) => x.id === c.id)
    if (idx >= 0) {
      favorites.value.splice(idx, 1)
      ElMessage.success('已取消收藏')
      return false
    }
    favorites.value.unshift({ ...c })
    ElMessage.success('已加入收藏')
    return true
  }

  // 移除指定收藏
  function removeFavorite(id: number) {
    const idx = favorites.value.findIndex((c) => c.id === id)
    if (idx >= 0) favorites.value.splice(idx, 1)
  }

  // 清空收藏
  function clearFavorites() {
    if (favorites.value.length === 0) {
      ElMessage.info('收藏夹为空')
      return
    }
    favorites.value.splice(0, favorites.value.length)
    ElMessage.success('收藏夹已清空')
  }

  // 导出收藏为 Markdown 文件
  function exportFavorites() {
    if (favorites.value.length === 0) {
      ElMessage.warning('收藏夹为空，无可导出内容')
      return
    }
    const lines: string[] = []
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`

    lines.push(`# 案例收藏夹`)
    lines.push('')
    lines.push(`> **导出时间**：${ts}`)
    lines.push(`> **案例数量**：${favorites.value.length} 条`)
    lines.push(`> **来源**：linzAI 法律助手 - 案例检索`)
    lines.push('---')
    lines.push('')

    favorites.value.forEach((c, i) => {
      lines.push(`## ${i + 1}. ${c.title || '未命名案例'}`)
      lines.push('')
      lines.push(`- **案由**：${c.caseCause || '—'}`)
      lines.push(`- **审理法院**：${c.court || '—'}`)
      lines.push(`- **审理年份**：${c.year ? c.year + ' 年' : '—'}`)
      if (c.source) {
        const src = c.source.startsWith('http') ? c.source : null
        lines.push(`- **案例来源**：${src ? `[${c.source}](${c.source})` : c.source}`)
      }
      lines.push('')
      if (c.summary) {
        lines.push('### 案件摘要')
        lines.push('')
        lines.push(c.summary)
        lines.push('')
      }
      lines.push('---')
      lines.push('')
    })

    lines.push(`*本收藏夹由 linzAI 法律助手生成，案例仅供参考，不构成法律意见。*`)

    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `linzAI案例收藏夹_${ts.replace(/[:\s]/g, '-')}.md`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('收藏夹已导出')
  }

  return {
    favorites,
    isFavorited,
    toggleFavorite,
    removeFavorite,
    clearFavorites,
    exportFavorites,
  }
}
