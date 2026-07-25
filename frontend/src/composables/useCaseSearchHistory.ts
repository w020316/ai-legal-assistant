import { ref, watch } from 'vue'
import type { CaseSearchRequest } from '@/api'

/**
 * 案例搜索历史（v1.11.0 新增）
 * <p>
 * 使用 localStorage 持久化用户最近的搜索条件，支持：
 * - 记录搜索（自动去重、最多保留 20 条）
 * - 列出历史
 * - 清空历史
 * - 移除单条历史
 * - 点击历史快速回填表单
 * <p>
 * 设计取舍：搜索条件属于轻量用户偏好，无需后端持久化，前端 localStorage 即可。
 * 与收藏夹不同，搜索历史只记录「查询条件」而非「查询结果」，避免占用过多存储空间。
 */

const STORAGE_KEY = 'linzai:caseSearchHistory'
const MAX_HISTORY = 20

// 历史记录项：包含搜索条件与可选标签（用于展示）
export interface SearchHistoryItem {
  // 唯一 id（时间戳 + 随机数），用于 v-for key
  id: string
  // 搜索条件快照
  query: CaseSearchRequest
  // 展示标签（自动拼接，便于用户识别）
  label: string
  // 记录时间戳（ISO 字符串）
  createdAt: string
}

// 全局共享的历史列表
const history = ref<SearchHistoryItem[]>(loadFromStorage())

function loadFromStorage(): SearchHistoryItem[] {
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
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history.value))
  } catch (e) {
    console.warn('搜索历史持久化失败:', e)
  }
}

// 自动持久化
watch(history, persist, { deep: true })

/**
 * 根据搜索条件生成可读标签
 * 例如：{ keyword: '违约金', cause: '合同纠纷' } → "违约金 · 合同纠纷"
 */
function buildLabel(query: CaseSearchRequest): string {
  const parts: string[] = []
  if (query.keyword?.trim()) parts.push(`“${query.keyword.trim()}”`)
  if (query.cause) parts.push(query.cause)
  if (query.courtLevel) parts.push(query.courtLevel)
  if (query.year) parts.push(`${query.year}年`)
  return parts.length ? parts.join(' · ') : '全部案例'
}

/**
 * 判断两条搜索条件是否等价（用于去重）
 */
function isSameQuery(a: CaseSearchRequest, b: CaseSearchRequest): boolean {
  return (
    (a.keyword?.trim() || '') === (b.keyword?.trim() || '') &&
    (a.cause || '') === (b.cause || '') &&
    (a.courtLevel || '') === (b.courtLevel || '') &&
    (a.year || 0) === (b.year || 0)
  )
}

export function useCaseSearchHistory() {
  /**
   * 记录一次搜索
   * - 与最近一次相同则跳过
   * - 超过上限时移除最旧的记录
   */
  function recordSearch(query: CaseSearchRequest) {
    // 跳过空查询（避免「点击重置」也被记录）
    const isEmpty =
      !query.keyword?.trim() && !query.cause && !query.courtLevel && !query.year
    if (isEmpty) return

    // 与最近一条相同则跳过
    if (history.value.length > 0 && isSameQuery(history.value[0].query, query)) return

    const item: SearchHistoryItem = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      query: { ...query },
      label: buildLabel(query),
      createdAt: new Date().toISOString(),
    }
    history.value.unshift(item)
    // 截断至最大长度
    if (history.value.length > MAX_HISTORY) {
      history.value.splice(MAX_HISTORY)
    }
  }

  /** 移除单条历史 */
  function removeHistory(id: string) {
    const idx = history.value.findIndex((h) => h.id === id)
    if (idx >= 0) history.value.splice(idx, 1)
  }

  /** 清空全部历史 */
  function clearHistory() {
    if (history.value.length === 0) return
    history.value.splice(0, history.value.length)
  }

  return {
    history,
    recordSearch,
    removeHistory,
    clearHistory,
  }
}
