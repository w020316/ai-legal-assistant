<script setup lang="ts">
import { computed } from 'vue'
import { render } from '@/utils/markdown'

const props = defineProps<{ content: string }>()

// 计算属性缓存渲染结果
const html = computed(() => render(props.content || ''))
</script>

<template>
  <div class="markdown-body" v-html="html"></div>
</template>

<style scoped lang="scss">
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-primary);
  word-break: break-word;

  :deep(p) {
    margin: 8px 0;
  }
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 16px 0 8px;
    font-weight: 600;
    color: var(--color-primary);
  }
  :deep(h1) {
    font-size: 20px;
  }
  :deep(h2) {
    font-size: 18px;
  }
  :deep(h3) {
    font-size: 16px;
  }
  :deep(ul),
  :deep(ol) {
    padding-left: 24px;
    margin: 8px 0;
  }
  :deep(li) {
    margin: 4px 0;
  }
  :deep(blockquote) {
    margin: 8px 0;
    padding: 8px 12px;
    border-left: 3px solid var(--color-accent);
    background: var(--color-bg);
    color: var(--color-text-regular);
    border-radius: 0 var(--radius-tag) var(--radius-tag) 0;
  }
  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 13px;
  }
  :deep(th),
  :deep(td) {
    border: 1px solid var(--color-border);
    padding: 8px 12px;
    text-align: left;
  }
  :deep(th) {
    background: var(--color-bg);
    font-weight: 600;
  }
  :deep(code) {
    background: rgba(0, 0, 0, 0.05);
    padding: 2px 4px;
    border-radius: var(--radius-tag);
    font-size: 13px;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  }
  :deep(pre) {
    margin: 12px 0;
    padding: 12px 16px;
    background: #f6f8fa;
    border-radius: var(--radius-button);
    overflow-x: auto;
    code {
      background: none;
      padding: 0;
      font-size: 13px;
    }
  }
  :deep(a) {
    color: var(--color-accent);
    text-decoration: underline;
  }
  :deep(hr) {
    border: none;
    border-top: 1px solid var(--color-border);
    margin: 16px 0;
  }
  :deep(img) {
    max-width: 100%;
    border-radius: var(--radius-tag);
  }
}
</style>
