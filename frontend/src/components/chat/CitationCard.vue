<script setup lang="ts">
import { ref } from 'vue'
import type { Citation } from '@/api'
import { Document, ArrowDown, ArrowUp } from '@element-plus/icons-vue'

defineProps<{ citations: Citation[] }>()

// 引用列表展开状态
const expanded = ref(false)
</script>

<template>
  <div v-if="citations && citations.length" class="citation-card">
    <div class="header" @click="expanded = !expanded">
      <el-icon><Document /></el-icon>
      <span class="title">引用来源（{{ citations.length }}）</span>
      <el-icon class="arrow">
        <ArrowUp v-if="expanded" />
        <ArrowDown v-else />
      </el-icon>
    </div>
    <div v-show="expanded" class="list">
      <div v-for="(c, i) in citations" :key="i" class="item">
        <div class="item-title">
          <span class="index">{{ i + 1 }}</span>
          <span class="text">{{ c.title }}</span>
        </div>
        <div v-if="c.source" class="item-source">来源：{{ c.source }}</div>
        <div v-if="c.snippet" class="item-snippet">{{ c.snippet }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.citation-card {
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  overflow: hidden;
}
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  color: var(--color-accent);
  font-size: 13px;
  font-weight: 500;
  user-select: none;
  .title {
    flex: 1;
  }
  .arrow {
    font-size: 12px;
  }
}
.list {
  border-top: 1px solid var(--color-border);
  padding: 8px 12px;
}
.item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-border);
  &:last-child {
    border-bottom: none;
  }
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  .index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-accent);
    color: #fff;
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
  padding-left: 26px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.item-snippet {
  margin-top: 4px;
  padding-left: 26px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.6;
}
</style>
