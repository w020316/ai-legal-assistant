import MarkdownIt from 'markdown-it'
// v1.11.0 优化：从 highlight.js/lib/core 按需导入语言，减少打包体积 ~950KB
// 法律 AI 场景常见代码块：JSON/SQL/JS/TS/Python/Bash/XML/Markdown/plaintext
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'
import markdownLang from 'highlight.js/lib/languages/markdown'
import plaintext from 'highlight.js/lib/languages/plaintext'
import katex from 'katex'

// 注册法律 AI 场景常用语言（其余语言自动回退到 plaintext）
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('python', python)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('markdown', markdownLang)
hljs.registerLanguage('plaintext', plaintext)
// 常见别名
hljs.registerAliases(['js'], { languageName: 'javascript' })
hljs.registerAliases(['ts'], { languageName: 'typescript' })
hljs.registerAliases(['py'], { languageName: 'python' })
hljs.registerAliases(['sh', 'shell'], { languageName: 'bash' })
hljs.registerAliases(['html'], { languageName: 'xml' })
hljs.registerAliases(['text', 'txt'], { languageName: 'plaintext' })

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

// v1.11.0 修复 H-3：拦截 javascript:/vbscript:/data: 等危险协议，防止 XSS
// 默认 markdown-it 仅在 html:true 时校验链接，html:false 时 linkify 出来的 <a> 不带协议校验
md.validateLink = (url: string) => {
  const trimmed = url.trim().toLowerCase()
  return !/^(javascript|vbscript|data|file):/i.test(trimmed)
}

// v1.11.0 修复 H-3：为所有渲染出的 <a> 强制添加安全属性，防止 window.opener 攻击与新窗口执行
const defaultLinkOpenRender = md.renderer.rules.link_open
  || ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens: any, idx: number, options: any, _env: any, self: any) => {
  const token = tokens[idx]
  const targetIndex = token.attrIndex('target')
  if (targetIndex < 0) {
    token.attrPush(['target', '_blank'])
  } else {
    token.attrs![targetIndex][1] = '_blank'
  }
  const relIndex = token.attrIndex('rel')
  if (relIndex < 0) {
    token.attrPush(['rel', 'noopener noreferrer nofollow'])
  } else {
    token.attrs![relIndex][1] = 'noopener noreferrer nofollow'
  }
  return defaultLinkOpenRender(tokens, idx, options, _env, self)
}

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
