<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Close } from '@element-plus/icons-vue'

// 当前版本号（与 package.json / MainLayout 保持一致）
const CURRENT_VERSION = 'v1.9.2'
// localStorage 存储键：记录用户上次已读版本
const STORAGE_KEY = 'linzai:lastReadVersion'
// 罗马数字映射（公报章节编号风）
const ROMAN_NUMERALS = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI']

const visible = ref(false)

// 阿拉伯索引转罗马数字（带防御，越界回退为阿拉伯数字）
function toRoman(index: number): string {
  return ROMAN_NUMERALS[index] ?? String(index + 1)
}

// 版本更新内容（按版本倒序）
interface ChangeLog {
  version: string
  date: string
  tag: string
  highlights: { title: string; desc: string }[]
}

const changelogs: ChangeLog[] = [
  {
    version: 'v1.9.2',
    date: '2026-07-24',
    tag: '安全加固',
    highlights: [
      { title: '后端密钥安全迁移', desc: 'JWT/管理员密码/API Key 从代码仓库迁移至 Fly secrets，移除默认值兜底' },
      { title: '认证安全闭环', desc: 'JWT 黑名单 Redis 故障时 fail-close，登出 token 不可绕过' },
      { title: 'AI 调用超时保护', desc: 'Tacklekey 客户端增加 60s 读取超时，线程池改为有界队列防 OOM' },
      { title: '前端死锁修复', desc: 'Token 刷新失败正确 reject 挂起请求，路由光标副作用移除，消息列表 key 修复' },
    ],
  },
  {
    version: 'v1.9.0',
    date: '2026-07-24',
    tag: '智能优化',
    highlights: [
      { title: '文档重新分析', desc: '清除旧分析结果后强制重新调用 AI，支持刷新过期或不满的分析报告' },
      { title: '长对话上下文压缩', desc: '滑动窗口 + 截断策略，历史超 4000 字符自动省略早期消息，防止 token 超限' },
    ],
  },
  {
    version: 'v1.8.0',
    date: '2026-07-24',
    tag: '管理后台',
    highlights: [
      { title: '审计日志前端面板', desc: 'ADMIN 可视化查看登录/登出/注册日志，含来源 IP 与用户详情' },
      { title: '管理员权限守卫', desc: '路由 meta.requireAdmin + 菜单动态渲染，非管理员不可见审计日志' },
      { title: '文档分析结果缓存', desc: '重复分析命中已有结果直接返回，跳过 AI 调用节省成本' },
    ],
  },
  {
    version: 'v1.7.0',
    date: '2026-07-24',
    tag: '工程加固',
    highlights: [
      { title: 'magic bytes 文件校验', desc: '读取文件头字节校验真实类型（PDF/DOCX/JPG/PNG），防伪造扩展名' },
      { title: 'N+1 查询优化', desc: 'PostgreSQL DISTINCT ON 批量查询，会话列表 1+N 次降为 1+1 次' },
      { title: '审计日志闭环', desc: '补全注册/登录/登出审计写入，记录来源 IP；新增 ADMIN 查看接口' },
    ],
  },
  {
    version: 'v1.6.0',
    date: '2026-07-24',
    tag: '文档处理升级',
    highlights: [
      { title: '图片文档支持', desc: '上传合同照片/截图（JPG/PNG），AI 自动识别文字并审查' },
      { title: '分析报告导出', desc: '一键导出 Markdown 格式合同审查报告，含评分/风险/缺失/义务' },
      { title: '法律三段论问答', desc: 'AI 回答采用大前提-小前提-结论推理结构，逻辑更严谨' },
    ],
  },
  {
    version: 'v1.5.0',
    date: '2026-07-24',
    tag: '合同审查深化',
    highlights: [
      { title: '双合同比对', desc: '上传两份合同，AI 逐条对比差异，给出风险变化方向与双评分' },
      { title: '风险财务估算', desc: '量化每个高风险条款可能带来的经济损失' },
      { title: '谈判建议优先级', desc: 'TIER 1/2/3 分级，明确哪些条款必须重谈、哪些可让步' },
      { title: '义务时间线', desc: '梳理合同中各方履行义务的截止日期与违约后果' },
    ],
  },
  {
    version: 'v1.4.3',
    date: '2026-07-24',
    tag: '文档分析增强',
    highlights: [
      { title: '安全评分仪表盘', desc: '0-100 环形评分，按分数段着色，一眼判断合同风险' },
      { title: '风险分级标签', desc: '高/中/低风险标签直观展示，与评分联动' },
      { title: '大白话说合同', desc: '条款通俗化总览 + 每条风险的大白话解释' },
      { title: '缺失条款提醒', desc: '自动识别应包含但未约定的条款，并给出补充建议' },
    ],
  },
  {
    version: 'v1.4.2',
    date: '2026-07-22',
    tag: 'AI 路由升级',
    highlights: [
      { title: 'Tacklekey 双模型路由', desc: '优先免费模型 openai/gpt-5.5:free，配额耗尽自动降级 Agnes' },
      { title: 'Fly.io CI/CD 自动部署', desc: 'GitHub Actions master 分支推送自动并行部署前后端' },
    ],
  },
  {
    version: 'v1.4.1',
    date: '2026-07-14',
    tag: '体验优化',
    highlights: [
      { title: '版本更新公告', desc: '登录后展示版本更新弹窗，重要功能不再错过' },
      { title: '代码质量加固', desc: 'v1.4 视觉重设计代码审查与回归验证' },
      { title: '文档同步', desc: '项目交付报告与用户体验评估报告同步更新' },
    ],
  },
  {
    version: 'v1.4.0',
    date: '2026-07-14',
    tag: '设计升华',
    highlights: [
      { title: 'The Verdict 编辑式法律公报', desc: '象牙纸 + 深墨黑 + 牛血红 + 古铜金四色调色板' },
      { title: '戏剧化衬线字体系统', desc: 'Cormorant Garamond + Source Serif 4 + Noto Serif SC' },
      { title: '公报式视觉语言', desc: '罗马数字章节、小型大写字母 eyebrow、花纹装饰、竖线引文' },
      { title: '硬编码颜色清理', desc: '12 处旧调色板硬编码统一为 var(--color-*)' },
    ],
  },
  {
    version: 'v1.3.0',
    date: '2026-07-10',
    tag: '品牌升级',
    highlights: [
      { title: '品牌改名 LawAI → linzAI', desc: '前端 8 处 + 标题 + 路由全量替换' },
      { title: '案例库扩充至 124 条', desc: '8 种案由 × 2015-2025 × 4 级法院' },
      { title: '模板原文查看', desc: 'el-tabs 标签页，默认显示完整模板原文' },
      { title: 'Dark Editorial UI 重设计', desc: '深色侧边栏 + 琥珀强调色 + 暖纸色背景' },
    ],
  },
]

onMounted(() => {
  // 已登录用户首次进入或版本升级时展示
  try {
    const lastRead = localStorage.getItem(STORAGE_KEY) || ''
    if (lastRead !== CURRENT_VERSION) {
      visible.value = true
    }
  } catch {
    // 隐私模式下 localStorage 可能不可用，安全降级：不展示
    visible.value = false
  }
})

// 关闭弹窗并记录已读版本
function handleClose() {
  visible.value = false
  try {
    localStorage.setItem(STORAGE_KEY, CURRENT_VERSION)
  } catch {
    // ignore
  }
}

// 查看详情按钮：跳转 GitHub Releases 后关闭
function handleViewDetails() {
  window.open('https://github.com/w020316/ai-legal-assistant/releases', '_blank', 'noopener')
  handleClose()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    width="560px"
    align-center
    class="version-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-eyebrow">RELEASE NOTES · 更新公告</span>
        <el-icon class="dialog-close" :size="18" @click="handleClose">
          <Close />
        </el-icon>
      </div>
    </template>

    <div class="dialog-body">
      <!-- 公报封面式标题 -->
      <div class="masthead">
        <span class="masthead-version">{{ CURRENT_VERSION }}</span>
        <span class="masthead-sep">❦</span>
        <span class="masthead-date">2026.07.14</span>
      </div>
      <div class="masthead-tagline">linzAI 法律助手 · The Verdict</div>

      <!-- 罗马数字分隔双线 -->
      <div class="rule-double"></div>

      <!-- 更新内容 -->
      <section
        v-for="(log, i) in changelogs"
        :key="log.version"
        class="changelog"
        :class="{ first: i === 0 }"
      >
        <header class="changelog-head">
          <span class="changelog-num">§ {{ toRoman(i) }}</span>
          <span class="changelog-version">{{ log.version }}</span>
          <span class="changelog-date">{{ log.date }}</span>
          <span class="changelog-tag">{{ log.tag }}</span>
        </header>
        <ul class="changelog-list">
          <li v-for="(h, j) in log.highlights" :key="j">
            <span class="changelog-title">{{ h.title }}</span>
            <span class="changelog-desc">— {{ h.desc }}</span>
          </li>
        </ul>
      </section>

      <!-- 花纹收尾 -->
      <div class="ornament">
        <span class="ornament-mark">❦</span>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-ghost" @click="handleClose">稍后查看</el-button>
        <el-button class="btn-primary" type="primary" @click="handleViewDetails">
          查看完整发布说明
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
// ===== 公报式对话框（The Verdict）=====
.version-dialog {
  :deep(.el-dialog) {
    background: var(--color-bg-card);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-card);
    box-shadow: 0 12px 48px rgba(20, 17, 15, 0.16), 0 0 0 1px rgba(20, 17, 15, 0.04);
    overflow: hidden;
  }
  :deep(.el-dialog__header) {
    padding: 0;
    margin: 0;
    border-bottom: 3px double var(--color-accent);
  }
  :deep(.el-dialog__body) {
    padding: 0;
  }
  :deep(.el-dialog__footer) {
    padding: 0;
    border-top: 1px solid var(--color-border-light);
  }
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--color-primary);
}
.dialog-eyebrow {
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.28em;
  color: var(--color-gilt);
  text-transform: uppercase;
}
.dialog-close {
  color: rgba(251, 248, 241, 0.6);
  cursor: pointer;
  transition: var(--transition-fast);
  &:hover {
    color: #FBF8F1;
    transform: rotate(90deg);
  }
}

.dialog-body {
  padding: 28px 32px 8px;
  background: var(--color-bg-card);
  // 象牙纸纤维纹理
  background-image:
    radial-gradient(rgba(122, 31, 43, 0.015) 1px, transparent 1px),
    radial-gradient(rgba(154, 107, 47, 0.012) 1px, transparent 1px);
  background-size: 18px 18px, 24px 24px;
  background-position: 0 0, 9px 12px;
}

// ===== 公报封面式刊头 =====
.masthead {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 16px;
}
.masthead-version {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 600;
  font-style: italic;
  color: var(--color-accent);
  letter-spacing: -0.01em;
  line-height: 1;
}
.masthead-sep {
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--color-gilt);
  opacity: 0.7;
}
.masthead-date {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  letter-spacing: 0.1em;
}
.masthead-tagline {
  margin-top: 6px;
  text-align: center;
  font-family: var(--font-serif);
  font-style: italic;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
}

// 公报双线分隔
.rule-double {
  margin: 18px 0 20px;
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
  height: 4px;
}

// ===== 章节式变更记录 =====
.changelog {
  margin-bottom: 18px;
  padding-left: 14px;
  border-left: 2px solid var(--color-border);
  transition: border-color 0.2s var(--ease-out);
  &.first {
    border-left-color: var(--color-accent);
  }
  &:hover {
    border-left-color: var(--color-accent);
  }
}
.changelog-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.changelog-num {
  font-family: var(--font-display);
  font-style: italic;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gilt);
}
.changelog-version {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}
.changelog-date {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-faint);
  letter-spacing: 0.08em;
}
.changelog-tag {
  font-family: var(--font-sans);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.2em;
  color: var(--color-accent);
  text-transform: uppercase;
  padding: 2px 8px;
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-tag);
}
.changelog-list {
  list-style: none;
  padding: 0;
  margin: 0;
  li {
    position: relative;
    padding-left: 14px;
    margin-bottom: 6px;
    font-family: var(--font-serif);
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--color-text-regular);
    &::before {
      content: '·';
      position: absolute;
      left: 4px;
      top: 0;
      color: var(--color-accent);
      font-weight: 700;
    }
    &:last-child {
      margin-bottom: 0;
    }
  }
}
.changelog-title {
  font-weight: 600;
  color: var(--color-text-primary);
}
.changelog-desc {
  color: var(--color-text-secondary);
  font-style: italic;
}

// 花纹收尾
.ornament {
  text-align: center;
  padding: 12px 0 4px;
  color: var(--color-gilt);
  opacity: 0.6;
}
.ornament-mark {
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.4em;
}

// ===== 底部按钮 =====
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  background: var(--color-bg-soft);
}
:deep(.btn-ghost) {
  font-family: var(--font-serif);
  font-size: 13px;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  &:hover {
    color: var(--color-text-primary);
    border-color: var(--color-text-faint);
    background: var(--color-bg-card);
  }
}
:deep(.btn-primary) {
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 600;
  background: var(--color-accent);
  border-color: var(--color-accent);
  border-radius: var(--radius-button);
  &:hover {
    background: var(--color-primary);
    border-color: var(--color-primary);
  }
}

// ===== 移动端响应式 =====
@media (max-width: 768px) {
  .version-dialog {
    :deep(.el-dialog) {
      width: 92vw !important;
      margin: 0 auto !important;
    }
  }
  .dialog-body {
    padding: 22px 20px 6px;
  }
  .masthead-version {
    font-size: 28px;
  }
  .masthead-sep {
    font-size: 14px;
  }
  .changelog-version {
    font-size: 16px;
  }
  .changelog-list li {
    font-size: 13px;
  }
  .dialog-footer {
    flex-direction: column-reverse;
    gap: 8px;
    padding: 14px 16px;
    :deep(.el-button) {
      width: 100%;
      margin: 0 !important;
    }
  }
}
</style>
