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
  font-size: 15px;
  line-height: 2;   /* v1.16 进一步放松，提升长文本阅读呼吸感 */
  color: var(--color-text-primary);
  word-break: break-word;

  :deep(p) {
    margin: 14px 0;
  }
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 26px 0 12px;
    font-weight: 600;
    color: var(--color-primary);
    line-height: 1.4;
  }
  :deep(h1) {
    font-size: 21px;
  }
  :deep(h2) {
    font-size: 18px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--color-border-light);
  }
  :deep(h3) {
    font-size: 16.5px;
  }
  :deep(strong) {
    color: var(--color-primary);
    font-weight: 600;
  }
  :deep(ul),
  :deep(ol) {
    padding-left: 28px;
    margin: 14px 0;
  }
  :deep(li) {
    margin: 10px 0;
  }
  :deep(li p) {
    margin: 6px 0;
  }
  :deep(blockquote) {
    margin: 12px 0;
    padding: 10px 16px;
    border-left: 3px solid var(--color-accent);
    background: linear-gradient(90deg, var(--color-accent-light), transparent 90%);
    color: var(--color-text-regular);
    border-radius: var(--radius-sm);
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

/* v1.16.0 移动端：页面固定、内容自适应 —— 正文进一步加大，提升小屏阅读舒适度 */
@media (max-width: 768px) {
  .markdown-body {
    font-size: 16px;
    line-height: 2.05;
  }
}
</style>
