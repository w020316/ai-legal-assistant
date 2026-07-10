<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const lastUpdated = '2026年7月10日'

interface LegalSection {
  num: string
  title: string
  intro?: string
  items?: { term: string; desc: string }[]
  paragraphs?: string[]
  note?: string
}

const sections: LegalSection[] = [
  {
    num: '01',
    title: '信息收集',
    intro:
      '为提供法律智能问答与文书分析服务，本平台会在您主动使用功能时收集以下信息。除法律法规另有规定外，本平台不会主动从第三方数据经纪商处获取您的个人信息。',
    items: [
      {
        term: '账号信息',
        desc: '您在注册时提交的用户名、密码（经哈希加密后存储）及选填邮箱，用于身份识别与账号管理。',
      },
      {
        term: '对话记录',
        desc: '您在问答会话中输入的提示词与平台返回的回答内容，用于维持会话上下文连贯性与服务质量评估。',
      },
      {
        term: '上传文档',
        desc: '您主动上传的 PDF、DOCX 等合同、协议或法律文书文件及其分析结果，用于提供风险识别与条款建议。',
      },
      {
        term: '使用日志',
        desc: '访问时间、IP 地址、浏览器类型、操作行为等技术日志，用于安全审计与故障排查，保留期不超过 90 天。',
      },
    ],
  },
  {
    num: '02',
    title: '信息使用',
    intro: '本平台仅在以下目的范围内使用所收集的个人信息，不会超出本政策所述目的进行处理：',
    items: [
      { term: 'AI 问答', desc: '将您的提问输入大语言模型，结合法律知识库生成回答，并保留会话历史以便您回顾。' },
      { term: '文档分析', desc: '解析您上传的文书内容，识别风险条款、缺失条款并给出修改建议与法律依据。' },
      { term: '服务改进', desc: '对脱敏后的问答与文档样本进行统计分析，优化模型回答质量与风险识别准确率。' },
      { term: '安全保障', desc: '识别异常登录、滥用行为与潜在攻击，保障账号与数据安全。' },
    ],
    note: '本平台不会将您的对话记录或上传文档用于训练面向公众开放的商业模型。',
  },
  {
    num: '03',
    title: '信息存储与保护',
    intro: '本平台采取多层技术与管理措施，保障您的个人信息安全：',
    items: [
      { term: '加密存储', desc: '密码采用加盐哈希算法存储，上传文档与分析结果在数据库中加密落盘，传输全程使用 HTTPS。' },
      { term: '数据隔离', desc: '不同账号的数据通过权限隔离机制严格分隔，您无法访问其他用户的对话记录与文档。' },
      { term: '访问控制', desc: '仅经授权的运维人员在最小必要原则下访问系统数据，操作行为留存审计日志。' },
      { term: '备份容灾', desc: '数据定期加密备份，备份数据与生产环境同等保护，确保服务连续性与数据可恢复性。' },
    ],
    note: '尽管本平台采取了合理的安全措施，但互联网传输与电子存储均不存在绝对安全的方式，无法保证百分之百不受未授权访问。',
  },
  {
    num: '04',
    title: '信息共享',
    intro:
      '本平台重视您的隐私，不会向第三方出售您的个人信息。在以下情形下，本平台可能在必要范围内共享信息：',
    items: [
      { term: '法律要求', desc: '应司法机关、行政机关依据法定程序提出的合法请求，本平台可能披露相关数据。' },
      { term: '服务提供方', desc: '为运行 AI 推理与文档解析，您的输入会传输至受保密协议约束的模型服务方，传输完成后即予删除。' },
      { term: '合并与转让', desc: '若本平台发生合并、收购或资产转让，相关信息将作为资产转移，本平台会提前通知您。' },
    ],
    note: '除上述情形外，本平台不会与任何第三方共享您的可识别个人信息。',
  },
  {
    num: '05',
    title: '用户权利',
    intro: '根据相关法律法规，您对自身个人信息享有以下权利，本平台将配合您行使：',
    items: [
      { term: '查看权', desc: '您可在个人中心随时查看自己的账号信息、历史对话记录与已上传文档列表。' },
      { term: '导出权', desc: '您可申请导出账号下的对话记录与文档分析报告，本平台将在 15 个工作日内提供结构化数据副本。' },
      { term: '删除权', desc: '您可删除单条对话或单个文档，也可申请注销账号并删除全部相关数据，删除操作不可恢复。' },
      { term: '更正权', desc: '您可自行修改用户名、邮箱等账号信息；若发现平台持有的数据有误，可申请更正。' },
      { term: '撤回同意', desc: '您可随时停止使用相关功能以撤回对应的信息处理授权，但不影响撤回前已进行的处理。' },
    ],
  },
  {
    num: '06',
    title: 'Cookie 与 Token 使用',
    intro: '为维持登录状态与识别会话，本平台使用以下技术手段：',
    items: [
      { term: '访问令牌', desc: '登录成功后签发 JWT 访问令牌，存储于本地，用于后续请求的身份验证，令牌默认有效期 2 小时。' },
      { term: '刷新令牌', desc: '用于在访问令牌过期时静默续期，刷新令牌有效期 7 天，退出登录后立即失效。' },
      { term: '会话存储', desc: '对话上下文标识保存在浏览器会话存储中，关闭浏览器后自动清除，不长期保留。' },
    ],
    note: '本平台不使用第三方广告追踪 Cookie，也不进行跨站行为画像。您可通过浏览器设置清除已存储的令牌与数据。',
  },
  {
    num: '07',
    title: '未成年人保护',
    paragraphs: [
      '本平台面向法律从业者及具备完全民事行为能力的成年人提供服务，不面向未满 18 周岁的未成年人。',
      '若您是未成年人，请在监护人陪同下阅读本政策并使用本平台，且应在监护人同意后提交个人信息。监护人发现未成年人在未经同意的情况下使用本平台，可联系本平台删除相关信息。',
      '本平台不会主动收集未成年人个人信息。若我们在知情情况下发现收集了未成年人的信息，将主动删除相关数据。',
    ],
  },
  {
    num: '08',
    title: '政策更新与联系方式',
    paragraphs: [
      '本平台可能根据法律法规调整或服务变化更新本隐私政策。重大变更时，本平台将在登录页面或通过站内通知提前告知您。政策更新后，您继续使用服务即视为同意更新后的内容。',
      '如您对本隐私政策有任何疑问、建议，或希望行使上述用户权利，可通过以下方式联系本平台：',
    ],
    items: [
      { term: '联系邮箱', desc: 'privacy@lawai.cn（隐私与数据保护事务）' },
      { term: '响应时限', desc: '本平台将在收到您的请求后 15 个工作日内予以回复。' },
    ],
  },
]
</script>

<template>
  <div class="page-container legal-page">
    <!-- 文档头 -->
    <header class="doc-head">
      <h1 class="page-title">隐私政策</h1>
      <p class="doc-meta">最近更新：<span class="meta-val">{{ lastUpdated }}</span></p>
      <p class="doc-intro">
        本隐私政策说明 LawAI 法律智能问答平台（以下简称"本平台"）如何收集、使用、存储和保护您的个人信息。请您在使用本平台前仔细阅读本政策。一旦您开始使用本平台提供的服务，即视为您已理解并同意本政策所述内容。
      </p>
    </header>

    <!-- 章节卡片 -->
    <section v-for="s in sections" :key="s.num" class="section-card">
      <div class="section-head">
        <span class="section-num">{{ s.num }}</span>
        <h2 class="section-title">{{ s.title }}</h2>
      </div>
      <div class="section-body">
        <p v-if="s.intro" class="intro">{{ s.intro }}</p>

        <ul v-if="s.items" class="legal-list">
          <li v-for="(it, i) in s.items" :key="i">
            <span class="term">{{ it.term }}</span><span class="colon">：</span>{{ it.desc }}
          </li>
        </ul>

        <p v-for="(p, i) in s.paragraphs" :key="i" class="para">{{ p }}</p>

        <p v-if="s.note" class="note">{{ s.note }}</p>
      </div>
    </section>

    <!-- 返回 -->
    <footer class="page-footer">
      <el-button @click="goBack">返回</el-button>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.legal-page {
  max-width: 860px;
  padding-top: 32px;
  padding-bottom: 48px;
  // 章节编号计数器，用于卡片左上角装饰
  counter-reset: section-counter;
}

/* 文档头 */
.doc-head {
  margin-bottom: 28px;
}
.doc-meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
  .meta-val {
    font-family: var(--font-mono);
    color: var(--color-text-regular);
  }
}
.doc-intro {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.85;
  color: var(--color-text-regular);
  max-width: 70ch;
}

/* 章节卡片 */
.section-card {
  position: relative;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 26px 30px;
  margin-bottom: 18px;
  counter-increment: section-counter;
  transition: box-shadow 0.2s var(--ease-out);
  &:hover {
    box-shadow: var(--shadow-hover);
  }
  // 左上角大号章节编号装饰：等宽字体 + 古铜色 20% 透明度
  &::before {
    content: counter(section-counter, decimal-leading-zero);
    position: absolute;
    top: 10px;
    left: 14px;
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    font-size: 42px;
    font-weight: 700;
    color: var(--color-accent);
    opacity: 0.2;
    line-height: 1;
    pointer-events: none;
    z-index: 0;
  }
}
.section-head {
  position: relative;
  z-index: 1;
}
.section-body {
  position: relative;
  z-index: 1;
}
.section-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}
.section-num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
  letter-spacing: 0.04em;
}
.section-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: -0.01em;
}

/* 章节正文 */
.section-body {
  font-size: 14px;
  line-height: 1.85;
  color: var(--color-text-regular);
  .intro {
    margin-bottom: 14px;
    max-width: 70ch;
  }
  .para {
    margin-bottom: 12px;
    max-width: 70ch;
    &:last-child {
      margin-bottom: 0;
    }
  }
}
.legal-list {
  list-style: none;
  padding: 0;
  margin: 0 0 14px;
  li {
    position: relative;
    padding-left: 20px;
    margin-bottom: 10px;
    line-height: 1.8;
    max-width: 68ch;
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 11px;
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: var(--color-accent);
    }
    &:last-child {
      margin-bottom: 0;
    }
  }
  .term {
    font-weight: 600;
    color: var(--color-primary);
  }
  .colon {
    color: var(--color-text-secondary);
  }
}
.note {
  margin-top: 14px;
  padding: 12px 16px;
  background: var(--color-accent-light);
  border-radius: var(--radius-card);
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 1.75;
  max-width: 68ch;
}

/* 返回按钮 */
.page-footer {
  margin-top: 12px;
  padding-top: 8px;
}

/* 响应式：移动端单列紧凑 */
@media (max-width: 768px) {
  .legal-page {
    padding: 20px 16px 32px;
  }
  .section-card {
    padding: 20px 18px;
    margin-bottom: 14px;
  }
  .section-head {
    gap: 10px;
    margin-bottom: 14px;
    padding-bottom: 10px;
  }
  .section-title {
    font-size: 17px;
  }
}
</style>
