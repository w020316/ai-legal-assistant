<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SessionVO } from '@/api'
import { Plus, Search, Star, StarFilled, Delete, Edit } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const props = defineProps<{
  sessions: SessionVO[]
  currentId?: number
}>()

const emit = defineEmits<{
  (e: 'select', session: SessionVO): void
  (e: 'create'): void
  (e: 'rename', id: number, title: string): void
  (e: 'toggleStar', session: SessionVO): void
  (e: 'delete', id: number): void
  (e: 'batchDelete', ids: number[]): void
}>()

const keyword = ref('')

// 多选模式
const multiSelectMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const isAllChecked = computed(() => {
  const list = filtered.value
  return list.length > 0 && selectedIds.value.size === list.length
})

function toggleMultiSelect() {
  multiSelectMode.value = !multiSelectMode.value
  if (!multiSelectMode.value) {
    selectedIds.value = new Set()
  }
}

function toggleSelect(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
  // 触发响应式更新
  selectedIds.value = new Set(selectedIds.value)
}

function toggleSelectAll() {
  if (isAllChecked.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(filtered.value.map((s) => s.id))
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.size} 个会话吗？此操作不可恢复。`, '批量删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
    emit('batchDelete', [...selectedIds.value])
    selectedIds.value = new Set()
    multiSelectMode.value = false
  } catch {
    // 用户取消
  }
}

// 按关键词过滤
const filtered = computed(() => {
  if (!keyword.value.trim()) return props.sessions
  const kw = keyword.value.toLowerCase()
  return props.sessions.filter((s) => s.title.toLowerCase().includes(kw))
})

// 收藏分组
const starredList = computed(() => filtered.value.filter((s) => s.starred))
const normalList = computed(() => filtered.value.filter((s) => !s.starred))

// 按时间分组
const groupedNormalList = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const weekAgo = new Date(today.getTime() - 7 * 86400000)

  const groups: { label: string; items: SessionVO[] }[] = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '过去7天', items: [] },
    { label: '更早', items: [] },
  ]

  for (const s of normalList.value) {
    const d = new Date(s.updatedAt)
    if (d >= today) groups[0].items.push(s)
    else if (d >= yesterday) groups[1].items.push(s)
    else if (d >= weekAgo) groups[2].items.push(s)
    else groups[3].items.push(s)
  }

  return groups.filter((g) => g.items.length > 0)
})

// 重命名会话
async function handleRename(session: SessionVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: session.title,
      inputPattern: /^.{1,128}$/,
      inputErrorMessage: '名称长度 1-128 位',
    })
    emit('rename', session.id, value)
  } catch {
    // 用户取消
  }
}

// 删除会话（二次确认）
async function handleDelete(session: SessionVO) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${session.title}」吗？此操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
    emit('delete', session.id)
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="session-list">
    <!-- 搜索 + 新建 + 批量操作 -->
    <div class="top-bar">
      <el-input v-if="!multiSelectMode" v-model="keyword" placeholder="搜索会话" :prefix-icon="Search" clearable size="small" />
      <span v-else class="select-info">已选 {{ selectedIds.size }} 项</span>
      <div class="top-actions">
        <template v-if="!multiSelectMode">
          <el-button size="small" @click="toggleMultiSelect">批量</el-button>
          <el-button type="primary" :icon="Plus" size="small" @click="emit('create')">新建</el-button>
        </template>
        <template v-else>
          <el-button size="small" @click="toggleSelectAll">
            {{ isAllChecked ? '取消全选' : '全选' }}
          </el-button>
          <el-button type="danger" size="small" :disabled="selectedIds.size === 0" @click="handleBatchDelete">
            删除选中
          </el-button>
          <el-button size="small" @click="toggleMultiSelect">退出</el-button>
        </template>
      </div>
    </div>
    <!-- 列表 -->
    <div class="list-scroll">
      <!-- 收藏分组 -->
      <template v-if="starredList.length">
        <div class="group-title">已收藏</div>
        <div
          v-for="s in starredList"
          :key="s.id"
          class="item"
          :class="{ active: s.id === currentId, selected: selectedIds.has(s.id) }"
          @click="multiSelectMode ? toggleSelect(s.id) : emit('select', s)"
        >
          <div class="item-main">
            <el-checkbox
              v-if="multiSelectMode"
              :model-value="selectedIds.has(s.id)"
              @change="toggleSelect(s.id)"
              @click.stop
              size="small"
            />
            <el-icon class="star-icon" @click.stop="emit('toggleStar', s)"><StarFilled /></el-icon>
            <span class="title">{{ s.title }}</span>
          </div>
          <div v-if="!multiSelectMode" class="item-actions">
            <el-icon @click.stop="handleRename(s)"><Edit /></el-icon>
            <el-icon @click.stop="handleDelete(s)"><Delete /></el-icon>
          </div>
        </div>
      </template>
      <!-- 普通分组（按时间分组） -->
      <template v-if="groupedNormalList.length">
        <template v-for="g in groupedNormalList" :key="g.label">
          <div class="group-title">{{ g.label }}</div>
          <div
            v-for="s in g.items"
            :key="s.id"
            class="item"
            :class="{ active: s.id === currentId, selected: selectedIds.has(s.id) }"
            @click="multiSelectMode ? toggleSelect(s.id) : emit('select', s)"
          >
            <div class="item-main">
              <el-checkbox
                v-if="multiSelectMode"
                :model-value="selectedIds.has(s.id)"
                @change="toggleSelect(s.id)"
                @click.stop
                size="small"
              />
              <el-icon class="star-icon" @click.stop="emit('toggleStar', s)"><Star /></el-icon>
              <span class="title">{{ s.title }}</span>
            </div>
            <div v-if="!multiSelectMode" class="item-actions">
              <el-icon @click.stop="handleRename(s)"><Edit /></el-icon>
              <el-icon @click.stop="handleDelete(s)"><Delete /></el-icon>
            </div>
          </div>
        </template>
      </template>
      <!-- 空状态 -->
      <el-empty v-if="!filtered.length" description="暂无会话" :image-size="60" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.top-bar {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
  // 搜索框样式优化：focus 时牛血红环
  :deep(.el-input__wrapper) {
    border-radius: var(--radius-button);
    transition: var(--transition-base);
    &.is-focus {
      box-shadow: 0 0 0 2px rgba(122, 31, 43, 0.12);
    }
  }
}
.top-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.select-info {
  font-size: 13px;
  color: var(--color-accent);
  line-height: 32px;
  font-weight: 500;
}
// 多选选中态：古铜浅底（非墨蓝深色）
.item.selected {
  background: var(--color-accent-light);
}
.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
// 时间分组标题：公报 eyebrow 式（小型大写字母 + 牛血红 + 字距加宽 + 双线分隔）
.group-title {
  padding: 14px 8px 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-accent);
  font-weight: 600;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  position: relative;
  &::after {
    content: '';
    display: block;
    margin-top: 6px;
    height: 1px;
    background: linear-gradient(to right, var(--color-accent) 0%, var(--color-accent) 24px, transparent 24px);
    opacity: 0.4;
  }
  &:not(:first-child) {
    margin-top: 6px;
  }
}
.item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 8px 14px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: var(--transition-base);
  // 入场动画
  animation: fadeInUp 0.3s var(--ease-out) both;
  // 左侧琥珀色竖条：hover 时从 0 生长到 100%
  &::before {
    content: '';
    position: absolute;
    left: 2px;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 0;
    border-radius: 0 2px 2px 0;
    background: var(--color-accent);
    transition: height 0.25s var(--ease-out);
  }
  &:hover {
    background: var(--color-bg-soft);
    &::before {
      height: 70%;
    }
    .item-actions {
      opacity: 1;
    }
  }
  // 激活态：古铜浅底 + 墨蓝文字（非墨蓝深底白字，更克制）+ 竖条常驻
  &.active {
    background: var(--color-accent-light);
    &::before {
      height: 70%;
    }
    .title {
      color: var(--color-primary);
      font-weight: 500;
    }
    .star-icon {
      color: var(--color-accent);
    }
    .item-actions {
      color: var(--color-text-secondary);
      opacity: 1;
    }
  }
}
.item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  .star-icon {
    font-size: 14px;
    color: var(--color-warning);
    flex-shrink: 0;
    transition: var(--transition-base);
    &:hover {
      color: var(--color-accent);
    }
  }
  .title {
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-text-regular);
  }
}
.item-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  color: var(--color-text-secondary);
  transition: opacity 0.15s;
  .el-icon {
    font-size: 14px;
    &:hover {
      color: var(--color-accent);
    }
  }
}
</style>
