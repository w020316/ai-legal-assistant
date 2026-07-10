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
    title: '服务描述',
    paragraphs: [
      'LawAI 法律智能问答平台（以下简称"本平台"）是一款面向法律从业者及有法律信息需求用户的辅助工具，提供基于人工智能的法律问答、文书分析与条款建议等功能。',
      '本平台通过大语言模型结合法律知识库生成回答，并在回答中标注可追溯的引用来源，帮助用户提升法律信息检索与文书审阅效率。本平台并非律师事务所，所提供的服务不构成律师执业行为。',
    ],
  },
  {
    num: '02',
    title: '使用条件',
    intro: '为保障服务安全与合规，使用本平台需满足以下条件：',
    items: [
      { term: '注册账号', desc: '您需注册专属账号并设置独立密码，账号仅限本人使用，不得出借、转让或共享给第三方。' },
      { term: '实名信息', desc: '根据法律法规要求，您应提供真实、准确的身份信息，并对账号下的所有活动负责。' },
      { term: '行为能力', desc: '您应具备完全民事行为能力；若您不具备，请在监护人陪同下使用本平台。' },
      { term: '合规使用', desc: '您承诺遵守中华人民共和国相关法律法规，不得将本平台用于任何违法或本协议禁止的用途。' },
    ],
    note: '本平台保留对账号注册申请进行审核与拒绝的权利，无需说明理由。',
  },
  {
    num: '03',
    title: '用户行为规范',
    intro: '使用本平台服务时，您不得从事以下行为，否则本平台有权限制或终止服务：',
    items: [
      { term: '违法使用', desc: '不得利用本平台从事危害国家安全、侵犯他人权益、传播违法信息等任何违反法律法规的活动。' },
      { term: '滥用 AI', desc: '不得通过自动化脚本批量调用、对模型进行恶意注入或诱导生成违法有害内容。' },
      { term: '侵犯隐私', desc: '不得在提问或上传文档中故意包含他人隐私信息、国家秘密或受保密义务保护的内容。' },
      { term: '破坏系统', desc: '不得攻击、干扰本平台服务，不得尝试未经授权访问系统数据或他人账号。' },
      { term: '伪造来源', desc: '不得将本平台回答断章取义、伪造引用来源后用于误导他人或诉讼欺诈。' },
    ],
    note: '如您发现他人存在上述违规行为，可向 legal@lawai.cn 举报，本平台将依法依规处理。',
  },
  {
    num: '04',
    title: '知识产权声明',
    items: [
      { term: '平台内容', desc: '本平台的界面设计、知识库、提示词模板及软件代码等知识产权归本平台所有，受法律保护，未经授权不得复制或商用。' },
      { term: '用户内容', desc: '您上传的文档与输入的提问，其知识产权归您或原权利人所有。您授予本平台在提供服务必要范围内使用该内容的非独占许可。' },
      { term: 'AI 生成内容', desc: '本平台生成的回答与分析结果，您可在合法用途下使用，但应注明来源。本平台不对生成内容的可版权性作出承诺。' },
      { term: '引用来源', desc: '回答中引用的法律条文、裁判文书等版权归原作者或发布机构所有，本平台仅作检索引用，不主张其权利。' },
    ],
  },
  {
    num: '05',
    title: '免责声明',
    paragraphs: [
      '本平台基于人工智能技术生成回答，受模型能力、训练数据与提问表述等因素影响，回答可能存在不准确、过时或不完整的情形。',
      '本平台回答仅供学习、研究与初步参考之用，不构成法律意见、律师建议或正式法律结论。涉及具体法律事务时，您应咨询具备执业资格的律师或专业法律机构。',
      '您不应仅依据本平台回答作出诉讼、签约、放弃权利等重大法律决策。因依赖本平台回答而造成的任何损失，本平台不承担赔偿责任。',
      '本平台对文档分析结果中的风险识别与条款建议不提供任何明示或默示担保，您应结合专业判断审慎采纳。',
    ],
    note: '法律法规存在修订与地域差异，本平台回答可能未及时反映最新变化，请以现行有效法律文本为准。',
  },
  {
    num: '06',
    title: '服务变更与终止',
    items: [
      { term: '服务变更', desc: '本平台可根据技术演进与运营需要调整、暂停或下线部分功能，重大变更将提前通过站内通知告知用户。' },
      { term: '用户终止', desc: '您可随时申请注销账号，注销后本平台将按隐私政策删除您的相关数据。' },
      { term: '平台终止', desc: '若您违反本协议或法律法规，本平台有权暂停或终止您的账号使用权限，并保留追究责任的权利。' },
      { term: '不可抗力', desc: '因自然灾害、政策调整、网络故障等不可抗力导致服务中断，本平台不承担责任，将尽快恢复服务。' },
    ],
  },
  {
    num: '07',
    title: '争议解决',
    paragraphs: [
      '本协议的订立、生效、解释与履行均适用中华人民共和国法律。',
      '因使用本平台产生的争议，双方应首先通过友好协商解决。协商不成的，任何一方均可向本平台运营方所在地有管辖权的人民法院提起诉讼。',
      '在争议解决期间，不涉及争议的条款仍应继续履行。',
    ],
  },
  {
    num: '08',
    title: '协议更新与联系方式',
    paragraphs: [
      '本平台可能不时修订本用户协议。修订后的协议将在本页面发布并更新"最近更新"日期，重大修订将提前通过站内通知告知您。修订后您继续使用服务即视为同意修订后的协议。',
      '若您对本用户协议有任何疑问或建议，可通过以下方式联系本平台：',
    ],
    items: [
      { term: '联系邮箱', desc: 'legal@lawai.cn（用户协议与合规事务）' },
      { term: '响应时限', desc: '本平台将在收到您的反馈后 15 个工作日内予以回复。' },
    ],
  },
]
</script>

<template>
  <div class="page-container legal-page">
    <!-- 文档头 -->
    <header class="doc-head">
      <h1 class="page-title">用户协议</h1>
      <p class="doc-meta">最近更新：<span class="meta-val">{{ lastUpdated }}</span></p>
      <p class="doc-intro">
        本用户协议（以下简称"本协议"）是您与 LawAI 法律智能问答平台（以下简称"本平台"）之间就使用本平台服务所订立的协议。请您在使用本平台前仔细阅读本协议。一旦您注册账号或开始使用服务，即视为您已阅读并同意受本协议约束。
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
