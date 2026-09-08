<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Citation } from '@/api'
import { Document, ArrowDown, ArrowUp } from '@element-plus/icons-vue'

const props = defineProps<{
  citations: Citation[]
  /** 当前应高亮引证索引（1-based），由消息正文 [N] 引证芯片触发 */
  activeIndex?: number
}>()

// 引用列表展开状态
const expanded = ref(false)
const highlight = ref<number | null>(null)

// 引证芯片点击/正文 [N] 标记触发：展开列表并短暂高亮对应条目
watch(
  () => props.activeIndex,
  (idx) => {
    if (idx != null && idx >= 1 && idx <= (props.citations?.length || 0)) {
      highlight.value = idx
      expanded.value = true
      // 高亮动画结束后清除，便于再次点击同一条目时重新触发
      window.setTimeout(() => {
        highlight.value = null
      }, 1400)
    }
  },
)
</script>

<template>
  <div v-if="citations && citations.length" class="citation-card">
    <div class="header" role="button" tabindex="0" :aria-expanded="expanded" aria-label="引用来源" @click="expanded = !expanded" @keydown.enter="expanded = !expanded">
      <el-icon><Document /></el-icon>
      <span class="title">引用来源（{{ citations.length }}）</span>
      <el-icon class="arrow">
        <ArrowUp v-if="expanded" />
        <ArrowDown v-else />
      </el-icon>
    </div>
    <transition name="cite-expand">
      <div v-show="expanded" class="list">
        <div
          v-for="(c, i) in citations"
          :key="i"
          class="item"
          :class="{ 'is-active': highlight === i + 1 }"
        >
          <div class="item-title">
            <span class="index">{{ i + 1 }}</span>
            <span class="text">{{ c.title }}</span>
          </div>
          <div v-if="c.source" class="item-source">来源：{{ c.source }}</div>
          <div v-if="c.snippet" class="item-snippet">{{ c.snippet }}</div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.citation-card {
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-left: 2px solid var(--color-accent);
  border-radius: var(--radius-card);
  // 牛血红浅底（公报引文式）
  background: var(--color-accent-light);
  overflow: hidden;
}
.header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  user-select: none;
  transition: background 0.15s;
  &:hover {
    background: rgba(122, 31, 43, 0.06);
  }
  .el-icon {
    color: var(--color-accent);
  }
  .title {
    flex: 1;
  }
  .arrow {
    font-size: 12px;
    transition: transform 0.2s var(--ease-out);
  }
}
.list {
  border-top: 1px solid rgba(122, 31, 43, 0.15);
  padding: 10px 14px;
  background: var(--color-bg-card);
}
.item {
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-light);
  &:last-child {
    border-bottom: none;
  }
  &.is-active {
    background: rgba(122, 31, 43, 0.06);
    border-left: 3px solid var(--color-accent);
    padding-left: 8px;
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    transition: background 0.25s, border-left 0.25s;
    .index {
      background: var(--color-accent);
      box-shadow: 0 0 0 3px rgba(122, 31, 43, 0.15);
    }
  }
}
.item-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  .index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: var(--radius-full);
    background: var(--color-accent);
    color: #FAFAF7;
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    font-size: 11px;
    flex-shrink: 0;
  }
  .text {
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
.item-source {
  margin-top: 4px;
  padding-left: 30px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-secondary);
}
.item-snippet {
  margin-top: 6px;
  padding-left: 30px;
  font-family: var(--font-serif);
  font-style: italic;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.7;
  // 公报引文式：左侧古铜金短竖线
  border-left: 2px solid var(--color-gilt);
  padding-left: 12px;
  margin-left: 16px;
}
// 平滑展开动画
.cite-expand-enter-active,
.cite-expand-leave-active {
  transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out);
  transform-origin: top;
}
.cite-expand-enter-from,
.cite-expand-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
