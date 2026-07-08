import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'

// 创建 markdown-it 实例
// - html: false 禁止原始 HTML，防止 XSS
// - linkify: 自动识别链接
// - 表格默认开启
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: false,
  highlight(code, lang) {
    // 代码高亮：返回高亮后的 HTML，空字符串回退到默认转义
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch {
        // 忽略高亮异常，回退默认
      }
    }
    return ''
  },
})

// KaTeX 块级公式渲染 $$...$$
function renderKatexBlock(expr: string): string {
  try {
    return katex.renderToString(expr, { throwOnError: false, displayMode: true })
  } catch {
    return expr
  }
}

// KaTeX 行内公式渲染 $...$
function renderKatexInline(expr: string): string {
  try {
    return katex.renderToString(expr, { throwOnError: false, displayMode: false })
  } catch {
    return expr
  }
}

// 渲染 Markdown 为 HTML 字符串
// 先渲染 markdown，再后处理 KaTeX 公式（法律文书场景可能含公式）
export function render(content: string): string {
  if (!content) return ''
  let html = md.render(content)
  // 块级公式 $$...$$
  html = html.replace(/\$\$([\s\S]+?)\$\$/g, (_m, expr: string) => renderKatexBlock(expr.trim()))
  // 行内公式 $...$（不跨行）
  html = html.replace(/\$([^\$\n]+?)\$/g, (_m, expr: string) => renderKatexInline(expr.trim()))
  return html
}

export default { render }
