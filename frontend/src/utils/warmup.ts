// 后端预热（Render free 实例闲置休眠后，首次请求需数十秒唤醒）
// 登录进入主界面后即静默预热：让冷启动发生在用户浏览/输入期间，
// 而非真正发消息那一刻，从而大幅缩短"首问等待"。
import http from '@/api/request'

let warmed = false

export function warmUpBackend() {
  // 仅预热一次；dev 不启用（本地无冷启动）
  if (warmed) return
  if (import.meta.env.DEV) return
  warmed = true
  // 用底层 axios 发一个轻量 health 请求，容忍冷启动（最长等 60s 后放弃），失败静默不影响体验
  http.raw
    .get('/health', { timeout: 60000 })
    .then(() => {
      // 预热成功（实例已热），无需展示
    })
    .catch(() => {
      // 冷启动超过 60s 或网络抖动：静默跳过，发送时仍会正常应答（仅首问略慢）
    })
}