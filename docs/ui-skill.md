# UI 设计技能（UI Skill）

> 参考 Taste Skill（Leonxlnx/taste-skill）+ Impeccable（pbakaus/impeccable）+ VibeCoding 思路整合，针对 AI 法律助手项目定制。
> 本文件是项目前端设计的唯一权威规范，所有前端页面变更必须遵循。

---

## 0. 设计读取框架（动手前必做）

生成或修改任何前端页面前，先输出一行"设计读取"：

```
Reading this as: <页面类型> for <受众>, with a <氛围> language, leaning toward <设计系统或美学族>。
```

**本项目固定读取**：
- 页面类型：产品 UI（product register，设计服务产品，非营销页）
- 受众：法律从业者（律师、法务、法学生），trust-first 优先
- 氛围：权威、克制、专业、可信赖
- 美学族：Soft Structuralism（柔和结构主义）+ 法律典籍编辑感
- 设计系统：Element Plus（已选型，不混用其他系统）

---

## 1. 三旋钮系统

| 旋钮 | 本项目值 | 含义 |
|---|---|---|
| `DESIGN_VARIANCE` | 5 | 偏对称克制，非对称留白仅用于登录/空状态 |
| `MOTION_INTENSITY` | 4 | 流畅 CSS 过渡，无 GSAP 滚动劫持，所有动效包 reduced-motion |
| `VISUAL_DENSITY` | 5 | 日常应用间距，数据列表略紧凑 |

旋钮门控：所有布局/动效/密度决策由旋钮值决定，不在对话中让用户编辑。

---

## 2. 配色系统（强制锁，全页一致）

### 2.1 调色板（OKLCH 思维，hex 落地）

| 角色 | 色值 | 用途 |
|---|---|---|
| 画布背景 | `#FAFAF7` | 主背景，暖白非纯白 |
| 主表面（卡片） | `#FFFFFF` | 卡片、表单、弹层 |
| 主色（权威墨蓝） | `#0B2545` | 顶部栏文字、标题、主按钮、激活态 |
| 主色悬浮 | `#133159` | 主按钮 hover |
| 强调色（典籍古铜） | `#8C6A3F` | 链接、引用来源、关键徽标（唯一强调色） |
| 强调色浅底 | `#F4EFE6` | 引用块背景、tag 背景 |
| 文字主 | `#0A0A0A` | 标题、正文主，近黑非纯黑 |
| 文字常规 | `#3D3D3D` | 次要正文 |
| 文字辅助 | `#8A8A8A` | 占位、说明、时间戳 |
| 边框发丝线 | `#E8E6E1` | 卡片边框、分割线，暖灰非冷灰 |
| 成功 | `#2D6A4F` | 深森林绿，状态徽标 |
| 警示 | `#9B2C2C` | 深酒红，风险点 |
| 警告 | `#9C6B0B` | 深琥珀 |

### 2.2 配色硬规则

- **一个项目一套调色板**：选定墨蓝 + 古铜后，全页锁定，不在第 7 节突然出现蓝色 CTA。
- **无纯黑 `#000` 无纯白 `#fff`**：所有中性色向暖色调色（chroma 0.005-0.01）。
- **LILA 规则**：禁用 AI 紫/蓝辉光渐变。登录页**不再使用** `linear-gradient(135deg, #1e3a5f, #2c7a7b)` 这类默认渐变。
- **WCAG AA**：正文对比 ≥ 4.5:1，大字 ≥ 3:1。古铜 `#8C6A3F` on 暖白对比 4.6:1 达标。
- **主题锁**：本项目固定浅色模式，各节不反转。深色模式未启用前不写半成品。

### 2.3 禁用配色（反 AI 默认）

禁用以下"AI 默认审美"色族作为主色或背景：
- AI 紫蓝渐变（`#1e3a5f → #2c7a7b`、`#667eea → #764ba2` 等）
- Material 默认蓝（`#1976d2`、`#2196f3`）
- Tailwind slate 冷灰（`#64748b`、`#94a3b8`）作中性色
- 高端消费品默认米色 + 黄铜族（`#f5f1ea` + `#b08947`）

---

## 3. 字体系统

### 3.1 字体栈

```scss
// 标题：中文衬线（法律典籍感），英文衬线 fallback
--font-serif: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', 'STSong', Georgia, serif;
// 正文：中文无衬线 + 英文 Grotesk
--font-sans: 'PingFang SC', 'Microsoft YaHei', -apple-system, 'Inter', sans-serif;
// 数字与代码：等宽
--font-mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', Consolas, monospace;
```

### 3.2 字体纪律

- **标题用衬线**：h1/h2 用 `--font-serif`，传递法律典籍的权威感。
- **正文用无衬线**：body、表单、列表用 `--font-sans`，保证可读性。
- **数字用等宽**：时间戳、统计、代码用 `--font-mono`，对齐整齐。
- **字重对比建层级**：标题 600 / 副标题 500 / 正文 400 / 辅助 400 + 浅色。步进 ≥ 1.25 比。
- **字号阶梯**：12 / 13 / 14 / 16 / 18 / 22 / 28 / 36（rem 化）。
- **禁尖叫超大 H1**：登录页 h1 ≤ 28px，不靠 `text-7xl` 堆视觉。

---

## 4. 形状与阴影系统

### 4.1 圆角（一套系统，全页统一）

```scss
--radius-sm: 3px;   // tag、徽标
--radius-md: 6px;   // 按钮、输入框、卡片（默认）
--radius-lg: 10px;  // 大卡片、弹层
--radius-full: 9999px; // 头像、圆点
```

法律产品克制感：默认 6px，非 12px+ 的消费品感。

### 4.2 阴影（超弥散，非刺眼深投影）

```scss
// 卡片默认：极轻，仅暗示层次
--shadow-card: 0 1px 2px rgba(11, 37, 69, 0.04), 0 2px 8px rgba(11, 37, 69, 0.03);
// 卡片悬浮：略增弥散
--shadow-hover: 0 2px 4px rgba(11, 37, 69, 0.06), 0 8px 24px rgba(11, 37, 69, 0.06);
// 弹层：向下弥散
--shadow-popover: 0 4px 12px rgba(11, 37, 69, 0.08), 0 16px 48px rgba(11, 37, 69, 0.08);
```

**禁**：`shadow-md/lg/xl` 默认深投影、`rgba(0,0,0,0.3)` 刺眼阴影。

### 4.3 边框纪律

- 默认 `1px solid var(--border)` 发丝线，暖灰 `#E8E6E1`。
- 列表分割线选 `border-bottom` **或** `border-top` 之一，不双线。
- 禁侧条纹边框（`border-left: 3px solid primary` 作强调），用背景染色或前置图标替代。

---

## 5. 布局原则

### 5.1 视口与容器

- 全高页用 `min-height: 100dvh`，**禁** `height: 100vh`（iOS Safari 地址栏跳动）。
- 主内容容器 `max-width: 1400px; margin: 0 auto;`。
- 正文行长封顶 65-75ch（`max-width: 70ch`）。

### 5.2 顶部栏与导航

- 顶部栏高度 **64px**（非 80px+ 巨型代理条）。
- 顶部栏背景：纯白 + 底部 1px 发丝线（**非**纯色填充 `--color-primary`）。
- 顶部栏文字：墨蓝 `#0B2545`，logo 衬线字体。
- 侧边栏宽度 240px，纯白 + 右侧 1px 发丝线。
- 桌面导航单行渲染，超 1024px 转汉堡。

### 5.3 间距节奏

- 节间距：`py-16`（64px）日常，`py-24`（96px）登录/空状态。
- 卡片内填充：`p-6`（24px）默认，`p-4`（16px）紧凑列表。
- 间距用 4 的倍数：4/8/12/16/24/32/48/64。

### 5.4 英雄区/登录页纪律

- 登录页**禁**居中蓝绿渐变背景 + 居中白卡（AI 默认）。
- 登录页改用**分屏**：左半品牌叙事（衬线大标题 + 价值主张 + 留白），右半表单。
- 表单卡 `max-width: 440px`，圆角 10px，超弥散阴影。
- 英雄区堆栈最多 4 文本元素：品牌条 / 标题 / 副文 / CTA。

### 5.5 Bento 与网格

- **禁三列等宽特性卡**：用 2 列锯齿、非对称网格、垂直堆叠替代。
- **禁 bento 白底白卡**：多 cell 网格至少 2-3 个 cell 有真实视觉变化（图标背景染色、引用块、数据）。
- 用 CSS Grid（`grid-cols-1 md:grid-cols-3`），**禁** flexbox 百分比数学。

---

## 6. 动效原则

### 6.1 动效必须有动机

加任何动画前问"这动画传达什么？"。有效答案：层级 / 叙事 / 反馈 / 状态转换。无效答案："看起来酷"。

### 6.2 本项目动效清单（MOTION_INTENSITY=4）

- 按钮 hover：`transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1)`，背景色过渡。
- 卡片 hover：`transform: translateY(-1px)` + 阴影升级，0.2s。
- 路由切换：`fade` 0.15s，无位移。
- SSE token 流入：逐字 `opacity` 0.1s，无位移。
- 加载态：骨架屏 shimmer，1.2s 循环。
- **禁**：marquee、视差、滚动劫持、`window.addEventListener('scroll')`、bounce/弹性缓动。

### 6.3 无障碍

所有动效包 `@media (prefers-reduced-motion: reduce)` 回退到瞬时。

---

## 7. 反廉价清单（Pre-Flight Check）

输出或修改前端页面后，逐项检查。任一失败必须重做。

### 7.1 视觉与 CSS

- [ ] 无 AI 紫蓝渐变背景（登录页禁 `linear-gradient(135deg, #1e3a5f, #2c7a7b)`）
- [ ] 无纯黑 `#000`、纯白 `#fff`，所有中性色向暖色调色
- [ ] 无刺眼深投影 `rgba(0,0,0,0.15)+`，用超弥散 `rgba(11,37,69,0.04)`
- [ ] 无过饱和强调色，古铜 `#8C6A3F` 已去饱和
- [ ] 无大标题渐变文字（`background-clip: text`）
- [ ] 无自定义鼠标光标

### 7.2 字体

- [ ] 标题用衬线 `--font-serif`，正文用 `--font-sans`
- [ ] 无尖叫超大 H1（登录页 h1 ≤ 28px）
- [ ] 字重对比建层级（600/500/400），非靠原始字号
- [ ] 数字用等宽 `--font-mono`

### 7.3 布局与间距

- [ ] 无居中渐变背景 + 居中白卡登录页（改分屏）
- [ ] 无三列等宽特性卡
- [ ] 无数学完美但漂浮的填充与边距
- [ ] eyebrow 克制（每 3 节最多 1 个，hero 算 1 个）
- [ ] 无分割头部（左大标题 + 右小说明）作默认节头
- [ ] 圆角全页统一 6px（非 12px/8px 混用）
- [ ] 顶部栏 64px + 纯白 + 发丝线（非纯色填充）

### 7.4 内容与数据

- [ ] 无通用名（"John Doe"、"张三"）→ 用真实本地化名
- [ ] 无假完美数字（`99.99%`）→ 用有机数据
- [ ] 无创业烂名（"Acme"、"SmartFlow"）
- [ ] 无填充动词（"赋能"、"无缝"、"下一代"、"颠覆"）→ 用具体动词

### 7.5 组件与资源

- [ ] 用 Element Plus 图标，**禁**手搓 SVG 图标
- [ ] 禁 div 假截图（`<div>` 矩形模拟假终端/假列表）
- [ ] Element Plus 组件须定制圆角/颜色/阴影到本项目美学，**非**默认状态发货

### 7.6 标点与文案

- [ ] **禁破折号 `—`**（最被违反的特征）：标题/eyebrow/正文/按钮全禁，用逗号/句号/括号或换行
- [ ] 中点 `·` 每行元数据最多 1 个
- [ ] 禁装饰性彩色状态点（仅真实语义状态用）
- [ ] 禁滚动提示（"Scroll"、"↓ scroll"）
- [ ] 禁地点/时间/天气条（"北京 14:23 · 18°C"）

---

## 8. Element Plus 定制覆盖

本项目用 Element Plus，须覆盖默认主题到本项目美学：

```scss
:root {
  // 主色覆盖为墨蓝
  --el-color-primary: #0B2545;
  --el-color-primary-light-3: #2C4A6E;
  --el-color-primary-light-5: #5A7A9E;
  --el-color-primary-light-7: #9AB0C8;
  --el-color-primary-light-9: #D8E0EC;
  --el-color-primary-dark-2: #061A33;

  // 强调色（古铜）用于 success/info 覆盖
  --el-color-success: #2D6A4F;
  --el-color-warning: #9C6B0B;
  --el-color-danger: #9B2C2C;
  --el-color-info: #8C6A3F;

  // 圆角
  --el-border-radius-base: 6px;
  --el-border-radius-small: 3px;
  --el-border-radius-round: 10px;

  // 字体
  --el-font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif;

  // 边框色（暖灰）
  --el-border-color: #E8E6E1;
  --el-border-color-light: #F0EEE9;
  --el-border-color-lighter: #F5F3EE;
}
```

---

## 9. 项目页面寄存器

| 页面 | 寄存器 | 关键规则 |
|---|---|---|
| LoginView | product + 品牌叙事 | 分屏布局，左品牌右表单，禁渐变背景 |
| MainLayout | product | 顶部栏 64px 纯白发丝线，侧边栏 240px 纯白 |
| ChatView | product + 数据密集 | 消息流左右分明，引用块古铜浅底，SSE 流入动效 |
| DocumentView | product + 表单 | 上传区虚线边框，风险点用酒红徽标 |
| TemplateView | product + 列表 | 模板卡片 2 列锯齿，非 3 列等宽 |
| CaseView | product + 搜索 | 搜索结果列表，分割线仅 `border-bottom` |
| HealthView | product + 仪表盘 | 状态徽标，数字等宽 |

---

## 10. Final Pre-Flight Check（发货前必跑）

修改前端任何页面后，逐项确认：

- [ ] 设计读取已声明？旋钮值符合本项目 5/4/5？
- [ ] 配色一致性锁（墨蓝 + 古铜全页）？无 AI 紫蓝渐变？
- [ ] 字体纪律（标题衬线 / 正文无衬线 / 数字等宽）？
- [ ] 圆角统一 6px？阴影超弥散 `rgba(11,37,69,0.04)`？
- [ ] 顶部栏 64px 纯白 + 发丝线？非纯色填充？
- [ ] 登录页分屏？非居中渐变白卡？
- [ ] 页面零破折号 `—`？无滚动提示？无装饰性彩色点？
- [ ] Element Plus 已定制覆盖？非默认状态发货？
- [ ] 动效包 reduced-motion？无 `window.addEventListener('scroll')`？
- [ ] 视口 `min-h-[100dvh]` 非 `h-screen`？
- [ ] 无 div 假截图、无手搓 SVG 图标、无通用名？

---

## 附：设计读取示例

**登录页改造前**：
> Reading this as: 居中渐变背景 + 居中白卡登录页，AI 默认审美，违反 LILA 规则与反廉价清单 7.1/7.3。

**登录页改造后**：
> Reading this as: 产品登录页 for 法律从业者, with 权威克制 language, leaning toward Soft Structuralism + 分屏布局, 衬线标题传递典籍感, 墨蓝 + 古铜配色锁, MOTION_INTENSITY=4 流畅 CSS 过渡。

---

**参考来源**：
- [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill)（MIT, Anti-Slop Frontend Framework）
- [pbakaus/impeccable](https://github.com/pbakaus/impeccable)（Apache 2.0, 23 命令 + 27 反模式）
- VibeCoding UI Skill（西瓜同学，抖音视频，10 种设计风格 + 配色系统 + 反廉价清单）
