<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="nf-inner">
      <span class="nf-eyebrow">ERROR · 页面失踪</span>
      <h1 class="nf-code">404</h1>
      <div class="nf-divider" aria-hidden="true">❦</div>
      <h2 class="nf-title">页面不存在</h2>
      <p class="nf-sub">您所寻找的页面可能已被移除、更名，或从未存在。</p>
      <el-button type="primary" size="large" class="nf-btn" @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 24px;
}
.nf-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 480px;
  animation: fadeInUp 0.6s var(--ease-out) both;
}
// 公报 eyebrow：小型大写字母 + 古铜金
.nf-eyebrow {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: var(--color-gilt);
  margin-bottom: 16px;
}
// 404 数字：戏剧化衬线巨字
.nf-code {
  font-family: var(--font-display);
  font-size: 144px;
  font-weight: 600;
  font-style: italic;
  line-height: 1;
  color: var(--color-accent);
  letter-spacing: -0.04em;
  margin-bottom: 8px;
}
// 花纹分隔
.nf-divider {
  font-size: 20px;
  color: var(--color-gilt);
  opacity: 0.7;
  margin: 8px 0 16px;
}
.nf-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: -0.015em;
  margin-bottom: 10px;
}
.nf-sub {
  font-family: var(--font-serif);
  font-style: italic;
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 28px;
  max-width: 36ch;
  line-height: 1.7;
}
.nf-btn {
  letter-spacing: 0.16em;
  font-family: var(--font-display);
  font-weight: 500;
}
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
