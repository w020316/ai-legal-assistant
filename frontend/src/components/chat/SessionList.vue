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
}>()

const keyword = ref('')

// 按关键词过滤
const filtered = computed(() => {
  if (!keyword.value.trim()) return props.sessions
  const kw = keyword.value.toLowerCase()
  return props.sessions.filter((s) => s.title.toLowerCase().includes(kw))
})

// 收藏分组
const starredList = computed(() => filtered.value.filter((s) => s.starred))
const normalList = computed(() => filtered.value.filter((s) => !s.starred))

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
    <!-- 搜索 + 新建 -->
    <div class="top-bar">
      <el-input v-model="keyword" placeholder="搜索会话" :prefix-icon="Search" clearable size="small" />
      <el-button type="primary" :icon="Plus" size="small" @click="emit('create')">新建</el-button>
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
          :class="{ active: s.id === currentId }"
          @click="emit('select', s)"
        >
          <div class="item-main">
            <el-icon class="star-icon" @click.stop="emit('toggleStar', s)"><StarFilled /></el-icon>
            <span class="title">{{ s.title }}</span>
          </div>
          <div class="item-actions">
            <el-icon @click.stop="handleRename(s)"><Edit /></el-icon>
            <el-icon @click.stop="handleDelete(s)"><Delete /></el-icon>
          </div>
        </div>
      </template>
      <!-- 普通分组 -->
      <template v-if="normalList.length">
        <div v-if="starredList.length" class="group-title">其他</div>
        <div
          v-for="s in normalList"
          :key="s.id"
          class="item"
          :class="{ active: s.id === currentId }"
          @click="emit('select', s)"
        >
          <div class="item-main">
            <el-icon class="star-icon" @click.stop="emit('toggleStar', s)"><Star /></el-icon>
            <span class="title">{{ s.title }}</span>
          </div>
          <div class="item-actions">
            <el-icon @click.stop="handleRename(s)"><Edit /></el-icon>
            <el-icon @click.stop="handleDelete(s)"><Delete /></el-icon>
          </div>
        </div>
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
}
.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.group-title {
  padding: 8px 8px 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 600;
}
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: var(--color-bg);
    .item-actions {
      opacity: 1;
    }
  }
  &.active {
    background: var(--color-primary);
    .title {
      color: #fff;
    }
    .star-icon {
      color: #fff;
    }
    .item-actions {
      color: #fff;
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
  }
  .title {
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
      color: var(--color-primary);
    }
  }
}
</style>
