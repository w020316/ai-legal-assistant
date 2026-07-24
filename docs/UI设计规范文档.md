# linzAI 法律助手 · UI 设计规范文档 v1.0

> **版本**：v1.10.0 · The Verdict Editorial Legal Gazette Design System
> **设计理念**：法律公报 / 司法典籍的编辑式美学
> **配色基调**：象牙纸 + 深墨黑 + 牛血红 + 古铜金
> **更新日期**：2026-07-24

---

## 一、设计调研基础

### 1.1 调研范围

本设计规范基于对 10 个法律科技 / SaaS 领域优质 UI 设计案例的系统性调研，覆盖：

| 类别 | 案例数 | 代表项目 |
|------|--------|----------|
| 法律科技产品 | 3 | Clio、Ironclad CLM、Harvey AI |
| 合同管理 / 审查 | 2 | DocuSign、PandaDoc |
| 法律 AI 助手 | 2 | Vishwajeet Lakra Legal AI Redesign、Malith Weeramuni Legal Dashboard |
| 通用 SaaS Dashboard | 3 | Booksprout、Landify CRM、Pixso 法律配色案例 |

详细调研见 `docs/UI设计调研报告.md`（10 个案例完整 URL + 设计亮点提取）。

### 1.2 行业共性设计语言

通过对 10 个案例的归纳，提炼出高端法律 SaaS 的 6 大共同特征：

1. **色彩克制权威**：深海军蓝 / 靛蓝 / 深紫为主色，传递专业可信
2. **衬线 + 无衬线混搭**：标题用衬线增强权威感，正文用无衬线保证可读性
3. **信息密度平衡**：卡片化分层 + 充足留白控制认知负担
4. **数据可视化**：KPI 卡片 + 状态色编码 + 流程时间轴
5. **信任优先的隐形设计**：引用可溯源、安全无感、人机边界清晰
6. **触控优先响应式**：576 / 768 / 992 / 1200px 四级断点体系

### 1.3 linzAI 设计差异化定位

linzAI 没有沿用行业主流的"深蓝 + 紫色"科技感路线，而是采用**"编辑式法律公报"**美学：

| 维度 | 行业主流 | linzAI 差异化 |
|------|----------|----------------|
| 主色 | 深海军蓝 / 靛蓝 | 深墨黑（暖调油墨色） |
| 强调色 | 蓝 / 紫渐变 | 牛血红（法官法袍色） |
| 装饰色 | 银色 / 浅灰 | 古铜金（法典烫金书脊） |
| 字体倾向 | 无衬线为主 | 戏剧化衬线为主 |
| 视觉语言 | 科技感 / 数据感 | 典籍感 / 公报感 |
| 整体气质 | 现代专业 | 庄重权威 + 戏剧化 |

**设计差异化价值**：在同质化的"蓝色科技风"法律产品中，linzAI 以"古典司法典籍现代化"的视觉语言形成强差异化识别，更适合面向中文法律场景用户。

---

## 二、色彩系统

### 2.1 主色系（Primary）

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-primary` | `#14110F` | 深墨黑（暖调，法典印刷油墨），主品牌色，标题 / 重要文字 |
| `--color-primary-light` | `#2A2520` | 浅一档墨黑，侧边栏背景渐变 |
| `--color-primary-soft` | `#4A4038` | 柔和墨色，次要标题 / hover 状态 |

**使用规范**：
- 主色用于：品牌 Logo、页面 H1-H3 标题、侧边栏背景、主按钮 hover 态
- 主色不用于：正文文字（用 `--color-text-regular`）、大面积背景（用 `--color-bg`）

### 2.2 强调色系（Accent）

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-accent` | `#7A1F2B` | 牛血红（法官法袍 / 司法印章色），CTA 按钮 / 强调元素 |
| `--color-accent-light` | `#F5E8EA` | 浅牛血红，info 标签背景 |
| `--color-accent-soft` | `#B85968` | 柔和牛血红，hover 态文字 |

**使用规范**：
- 强调色面积控制在 10% 以内，避免视觉过载
- 强调色用于：主 CTA 按钮、链接、eyebrow 小型大写字母标签、active 菜单指示器
- 强调色不用于：正文段落文字（容易刺眼）

### 2.3 辅助色系（Gilt · 古铜金）

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-gilt` | `#9A6B2F` | 古铜金（法典烫金书脊），纯装饰用 |
| `--color-gilt-light` | `#F0E6D2` | 浅古铜金，hover 背景 |

**使用规范**：
- 古铜金仅用于装饰元素：罗马数字章节编号、masthead eyebrow、ornament 花纹
- 古铜金不用于功能性按钮或文字（可读性差）

### 2.4 语义色（Semantic）

| Token | 色值 | 语义 |
|-------|------|------|
| `--color-danger` | `#8B1E1E` | 高风险 / 错误 / 删除 |
| `--color-warning` | `#8B6914` | 中风险 / 警告 / 待处理 |
| `--color-success` | `#3D5C3A` | 低风险 / 成功 / 完成 |

**使用规范**：
- 三色均为低饱和度暖色（与整体象牙纸基调和谐）
- 用于：风险等级标签（高 / 中 / 低）、状态徽标、表单验证反馈
- 不与牛血红强调色混淆：危险 / 错误统一用 `--color-danger`，CTA 用 `--color-accent`

### 2.5 背景色系（Background · 象牙纸）

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-bg` | `#FBF8F1` | 页面主背景，象牙纸基调 |
| `--color-bg-card` | `#FEFCF6` | 卡片背景，比页面稍亮 |
| `--color-bg-soft` | `#F4EFE3` | 次级面板背景（填充 / hover） |
| `--color-bg-deep` | `#14110F` | 深色面板（登录页左半 / 品牌区） |

**使用规范**：
- 页面背景 + 卡片背景 + 次级背景形成三层视觉层级
- 所有背景色均为低饱和度暖白，模拟古籍纸张质感
- 禁止使用纯白（`#FFFFFF`），会破坏整体基调

### 2.6 文字色系

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-text-primary` | `#14110F` | 标题 / 重要文字 |
| `--color-text-regular` | `#3A352F` | 正文文字 |
| `--color-text-secondary` | `#7A726A` | 次要文字 / 标签 |
| `--color-text-faint` | `#A89F95` | 占位符 / 弱化文字 |

### 2.7 边框色系

| Token | 色值 | 用途 |
|-------|------|------|
| `--color-border` | `#D9D2C2` | 卡片边框（默认） |
| `--color-border-light` | `#E8E2D5` | 弱边框（分割线） |
| `--color-border-strong` | `#14110F` | 强边框（公报表单感） |
| `--color-rule` | `#14110F` | 公报竖线分隔（实线深墨） |

---

## 三、字体排版规范

### 3.1 字体栈

```scss
--font-display: 'Cormorant Garamond', 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif;
--font-serif:   'Source Serif 4', 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif;
--font-sans:    'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', -apple-system, sans-serif;
--font-mono:    'JetBrains Mono', 'SF Mono', 'Cascadia Code', Consolas, monospace;
```

| 字体栈 | 用途 | 字体特征 |
|--------|------|----------|
| `--font-display` | 戏剧化标题、罗马数字、Logo | 高对比衬线，法律典籍权威感 |
| `--font-serif` | 正文、法律文书阅读 | 可读性优先衬线 |
| `--font-sans` | UI 控件、标签、小字 | 现代无衬线 |
| `--font-mono` | 法条编号、时间戳、数据 | 等宽字体，数字对齐 |

### 3.2 字体层级

| 层级 | 字体栈 | 字号 | 字重 | 行高 | 字间距 | 用途 |
|------|--------|------|------|------|--------|------|
| Display | `--font-display` | 48px+ | 600 | 1.15 | -0.02em | Hero 大标题 |
| H1 | `--font-display` | 35px | 600 | 1.2 | -0.015em | 页面主标题 |
| H2 | `--font-display` | 26px | 600 | 1.2 | -0.015em | 区块标题 |
| H3 | `--font-display` | 20px | 600 | 1.3 | -0.01em | 卡片标题 |
| H4 | `--font-display` | 17px | 600 | 1.35 | -0.01em | 小节标题 |
| Body | `--font-serif` | 15px | 400 | 1.65 | 0 | 正文 |
| Body Small | `--font-serif` | 13px | 400 | 1.7 | 0 | 次要正文 |
| Caption | `--font-sans` | 12px | 500 | 1.5 | 0 | 说明文字 |
| Eyebrow | `--font-sans` | 11px | 600 | 1.4 | 0.2em (uppercase) | 小型大写标签 |
| Mono | `--font-mono` | 11-13px | 500 | 1.5 | 0.08em | 数据 / 时间戳 |

### 3.3 排版规范

- **正文行距**：1.65 倍（法律文书阅读舒适）
- **段落间距**：`var(--space-md)` (16px)
- **首字下沉**：长文段落使用 `.drop-cap` 类，3.4em 字号，牛血红
- **数字格式**：等宽字体 + `font-variant-numeric: tabular-nums`，确保数据列对齐
- **中文标点**：开启 `font-feature-settings: 'kern', 'liga', 'onum'`（旧式数字，古籍感）

---

## 四、组件设计规范

### 4.1 按钮（Button）

| 类型 | 样式 | 用途 |
|------|------|------|
| Primary | 牛血红实色 + 直角 + hover 墨黑 | 主 CTA（提交、确认） |
| Default | 透明 + 深墨边框 + hover 牛血红 | 次 CTA（重置、取消） |
| Text | 无背景 + hover 牛血红 | 文字操作（查看详情） |
| Danger | 危险红 + plain 模式 | 删除、清空 |

**尺寸规范**：
- Default: height 32px, padding 12px 20px, font-size 13px
- Small: height 28px, padding 8px 16px, font-size 12px
- Large: height 40px, padding 16px 28px, font-size 14px

**圆角**：`--radius-button` = 2px（法律典籍直角感）

**过渡动画**：`all 0.15s var(--ease-out)`，hover 上浮 1px

### 4.2 卡片（Card）

```scss
.el-card {
  border-radius: var(--radius-card);  // 3px
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);  // 0 1px 2px rgba(20, 17, 15, 0.03)
  &:hover {
    border-color: var(--color-border-strong);
    box-shadow: var(--shadow-hover);
  }
}
```

**阴影系统**（极克制，靠竖线与留白分区）：
- `--shadow-card`: `0 1px 2px rgba(20, 17, 15, 0.03)` 默认
- `--shadow-hover`: `0 2px 8px rgba(20, 17, 15, 0.06), 0 0 0 1px rgba(122, 31, 43, 0.08)`
- `--shadow-popover`: `0 4px 16px rgba(20, 17, 15, 0.08), 0 0 0 1px rgba(20, 17, 15, 0.06)`

### 4.3 输入框（Input）

- **默认态**：浅色背景 + 暖灰边框
- **聚焦态**：牛血红边框 + `box-shadow: 0 0 0 1px var(--color-accent)`
- **圆角**：`--radius-sm` = 2px
- **字体**：`--font-sans` 无衬线（UI 输入场景）

### 4.4 标签（Tag）

- **圆角**：`--radius-tag` = 1px（近直角）
- **字号**：11px
- **字重**：500
- **字间距**：0.05em
- **类型**：
  - `info`：浅牛血红背景 + 牛血红文字
  - `success`：浅绿背景 + 绿色文字
  - `warning`：浅橙背景 + 橙色文字
  - `danger`：浅红背景 + 红色文字

### 4.5 表格（Table）

- **斑马纹**：奇数行 `--color-bg-card`，偶数行 `--color-bg-soft`
- **悬浮高亮**：hover 行背景 `--color-accent-light`
- **固定表头**：sticky 定位，背景 `--color-primary`，文字 `#FBF8F1`
- **边框**：仅水平分隔线，无垂直分隔线（公报表格感）

### 4.6 抽屉（Drawer）

- **宽度**：520px（桌面端默认）
- **圆角**：左侧 0，右侧 0（贴边）
- **背景**：`--color-bg-card`
- **标题**：`--font-display` 18px 600
- **动画**：0.4s `var(--ease-out)` 滑入

### 4.7 对话框（Dialog）

- **圆角**：`--radius-card` = 3px
- **头部**：深墨背景 + 古铜金 eyebrow + 双线分隔（`3px double var(--color-accent)`）
- **主体**：象牙纸 + 极浅纸张纹理
- **底部**：浅米色背景 + 主按钮右对齐

### 4.8 消息提示（Message）

- **位置**：顶部居中
- **圆角**：`--radius-card` = 3px
- **字体**：`--font-sans` 13px
- **动画**：从顶部滑入，3 秒后自动消失

---

## 五、布局规范

### 5.1 间距系统

基于 4px 基线网格：

| Token | 值 | 用途 |
|-------|----|------|
| `--space-xs` | 4px | 微间距（图标与文字） |
| `--space-sm` | 8px | 紧凑间距（标签内边距） |
| `--space-md` | 16px | 标准间距（卡片间距） |
| `--space-lg` | 24px | 大间距（区块间距） |
| `--space-xl` | 40px | 超大间距（页面内边距） |
| `--space-2xl` | 64px | 区块间距（Hero 区） |

### 5.2 圆角系统

| Token | 值 | 用途 |
|-------|----|------|
| `--radius-none` | 0 | 装饰分隔线 |
| `--radius-sm` | 2px | 输入框、小元素 |
| `--radius-card` | 3px | 卡片、对话框 |
| `--radius-button` | 2px | 按钮 |
| `--radius-lg` | 4px | 大卡片 |
| `--radius-tag` | 1px | 标签 |
| `--radius-full` | 9999px | 圆形元素（头像、徽章） |

**设计取舍**：圆角统一控制在 1-4px，传递法律典籍的"直角庄重感"，区别于消费级 App 的 8-12px 圆角。

### 5.3 主布局（MainLayout）

```
┌──────────────────────────────────────────────────────┐
│ Header (64px) · 深墨背景 + 古铜金 eyebrow + 牛血红线  │
├──────────┬───────────────────────────────────────────┤
│          │                                            │
│ Aside    │  Main Content (象牙纸背景)                  │
│ (240px)  │                                            │
│ 深墨背景  │  · padding: 20px                          │
│ 罗马数字  │  · router-view + route-fade transition    │
│ 菜单      │                                            │
│          │                                            │
├──────────┤                                            │
│ 法务链接  │                                            │
│ 版本号    │                                            │
└──────────┴───────────────────────────────────────────┘
```

### 5.4 响应式断点

| 断点 | 宽度 | 适配设备 | 主要变化 |
|------|------|----------|----------|
| Desktop | ≥ 1200px | 桌面端 | 240px 侧边栏 + 完整布局 |
| Tablet Landscape | 1024-1199px | 平板横屏 | 侧边栏可折叠 + 卡片网格调整 |
| Tablet | 769-1023px | 平板竖屏 | 侧边栏可折叠 + 单列卡片 |
| Mobile | 481-768px | 手机 | 抽屉式导航 + 单列布局 + 简化标题 |
| Small Mobile | ≤ 480px | 小屏手机 | 隐藏装饰元素 + 压缩间距 + 全宽按钮 |

**关键响应式规则**：
1. **768px 以下**：侧边栏改为抽屉式导航，搜索栏改为垂直堆叠
2. **480px 以下**：隐藏 masthead eyebrow，压缩 header 高度至 52px，按钮全宽
3. **触控友好**：移动端按钮最小高度 36px，触摸目标 ≥ 44x44px
4. **收藏按钮移动端常显**：避免触摸不易触发的 hover 问题

---

## 六、视觉装饰元素

### 6.1 公报式装饰组件

| 组件类名 | 用途 | 视觉特征 |
|----------|------|----------|
| `.page-title[data-eyebrow]` | 页面主标题 | 上方 eyebrow + 下方牛血红双线 |
| `.roman-numeral` | 章节编号 | 罗马数字斜体 + 牛血红 |
| `.eyebrow` | 小型大写标签 | 11px 600 + 0.18em 字间距 |
| `.drop-cap` | 首字下沉 | 3.4em + 牛血红 + 左浮动 |
| `.gazette-divider` | 章节分隔符 | 居中花纹 + 两侧细线 |
| `.pull-quote` | 引文块 | 左侧 3px 牛血红竖线 + 斜体 |

### 6.2 动画系统

| 关键帧 | 时长 | 用途 |
|--------|------|------|
| `fadeInUp` | 0.5s | 卡片入场 |
| `slideInLeft` | 0.4s | AI 消息入场 |
| `slideInRight` | 0.4s | 用户消息入场 |
| `pulseDot` | 1.4s infinite | AI 思考中 |
| `shimmer` | 1.6s infinite | 骨架屏光泽 |
| `breathe` | 3s infinite | 徽章光晕 |
| `blink` | 1s infinite | 流式光标 |
| `spin` | 1s infinite | 加载旋转 |

**staggered 入场**：列表项逐个延迟入场，`.stagger-1` 到 `.stagger-6` 分别延迟 0.05s 到 0.3s。

### 6.3 无障碍设计

```scss
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

- 尊重 `prefers-reduced-motion`，所有动画降级为瞬时切换
- 颜色对比度满足 WCAG AA 标准（正文 ≥ 4.5:1，大字 ≥ 3:1）
- 所有交互元素支持键盘导航
- 图片提供 alt 文本，图标提供 aria-label

---

## 七、页面设计规范

### 7.1 智能问答页（ChatView）

- **布局**：左侧会话列表（260px）+ 右侧消息流
- **消息气泡**：用户消息右对齐牛血红背景，AI 消息左对齐卡片背景
- **流式输出**：逐字显示 + 末尾闪烁光标
- **引用卡片**：可折叠的法律条文引用，点击跳转原文
- **移动端**：会话列表改为顶部抽屉

### 7.2 文档分析页（DocumentView）

- **布局**：左侧文档列表（240px）+ 右侧分析报告
- **安全评分仪表盘**：环形进度条 + 分数段着色（红/橙/绿）
- **风险点卡片**：风险等级标签 + 谈判优先级 TIER 标识
- **大白话解释**：可折叠面板，默认收起
- **报告导出**：Markdown 格式，含评分 / 风险 / 缺失 / 义务 / 财务估算

### 7.3 案例检索页（CaseView）

- **布局**：顶部搜索栏 + 列表卡片 + 详情抽屉
- **视图模式**：检索结果 / 我的收藏 双视图切换
- **收藏交互**：卡片右上角星标按钮（hover 显示，移动端常显）
- **卡片入场**：staggered fadeInUp 动画
- **角标装饰**：左上角案由首字（公报章节式）

### 7.4 文书模板页（TemplateView）

- **布局**：左侧分类导航 + 右侧模板列表
- **Tabs 切换**：模板原文 / AI 生成（默认显示原文）
- **生成表单**：分步引导 + 实时验证

### 7.5 系统状态页（HealthView）

- **布局**：单列居中（max-width 720px）
- **状态主卡**：顶部 2px 状态色边框 + 徽标 + 服务名 + 检测时间
- **详情卡片网格**：2 列响应式（移动端变单列）

### 7.6 审计日志页（AuditView · ADMIN 专属）

- **布局**：顶部筛选栏 + 表格 + 分页
- **表格**：斑马纹 + 悬浮高亮 + 固定表头
- **权限守卫**：路由 meta.requireAdmin + 菜单动态渲染

---

## 八、Element Plus 主题覆盖

```scss
:root {
  // 主色映射到 Element Plus
  --el-color-primary: #7A1F2B;
  --el-color-primary-light-3: #9E4450;
  --el-color-primary-light-5: #C2858E;
  --el-color-primary-light-7: #D9BCC1;
  --el-color-primary-light-9: #F5E8EA;
  --el-color-primary-dark-2: #5C1620;

  // 语义色
  --el-color-success: #3D5C3A;
  --el-color-warning: #8B6914;
  --el-color-danger: #8B1E1E;
  --el-color-info: #7A726A;

  // 圆角（克制）
  --el-border-radius-base: 3px;
  --el-border-radius-small: 2px;
  --el-border-radius-round: 4px;

  // 字体
  --el-font-family: var(--font-serif);

  // 边框（暖灰）
  --el-border-color: #D9D2C2;
  --el-border-color-light: #E8E2D5;
  --el-border-color-lighter: #F0EDE3;

  // 文字
  --el-text-color-primary: #14110F;
  --el-text-color-regular: #3A352F;
  --el-text-color-secondary: #7A726A;
  --el-text-color-placeholder: #A89F95;

  // 背景
  --el-bg-color: #FEFCF6;
  --el-bg-color-page: #FBF8F1;
  --el-fill-color-light: #F4EFE3;
  --el-fill-color: #F4EFE3;
  --el-fill-color-blank: #FEFCF6;
}
```

---

## 九、设计稿与样式规范

### 9.1 设计稿交付

- **源文件位置**：`frontend/src/styles/main.scss`（单一真源，所有设计 token 集中管理）
- **组件库**：基于 Element Plus + 全局 SCSS 覆盖
- **图标库**：`@element-plus/icons-vue`
- **字体加载**：`index.html` 中通过 Google Fonts + 字体回退栈

### 9.2 样式规范文档

本文件即为完整样式规范文档，覆盖：
- 色彩系统（7 大类，30+ token）
- 字体排版（4 字体栈 + 10 级层级）
- 组件设计（8 大组件规范）
- 布局规范（间距 / 圆角 / 主布局 / 5 级响应式断点）
- 视觉装饰（6 个公报式组件 + 8 个动画关键帧）
- Element Plus 主题覆盖（完整 token 映射）

### 9.3 后续优化方向（v1.11.0+）

基于调研结论，未来可考虑的优化方向：

1. **暗色模式支持**：参考 Harvey AI 的深色模式，增加 `prefers-color-scheme: dark` 适配
2. **数据可视化增强**：参考 Ironclad 的工作流可视化，为合同审查增加流程图
3. **KPI 卡片标准化**：参考 Booksprout，为 Dashboard 增加趋势箭头 + 迷你图表
4. **首屏 Hero 优化**：参考 Clio，登录后首页增加数据驱动的 Hero 区
5. **引用溯源交互**：参考 Harvey AI，AI 回答中的法条引用支持点击跳转原文

---

## 十、设计版本变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.4.0 | 2026-07-14 | The Verdict 编辑式法律公报设计系统首版发布 |
| v1.10.0 | 2026-07-24 | 响应式断点补全（1024 / 480px）+ 案例收藏 UI |
| v1.10.0 | 2026-07-24 | UI 设计规范文档 v1.0 发布（本文件） |

---

*本设计规范由 linzAI 法律助手项目维护，遵循"古典司法典籍现代化"的设计哲学。*
