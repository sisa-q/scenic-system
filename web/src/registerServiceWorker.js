/* eslint-disable no-console */
import { register } from 'register-service-worker'

if (process.env.NODE_ENV === 'production') {
  register(`${process.env.BASE_URL}service-worker.js`, {
    ready() { console.log('PWA 已就绪，可离线使用') },
    registered() { console.log('Service Worker 已注册') },
    cached() { console.log('内容已缓存，支持离线') },
    updatefound() { console.log('发现新版本，正在下载') },
    updated() { console.log('新版本已就绪，请刷新页面') },
    offline() { console.log('当前处于离线模式') },
    error(e) { console.error('SW 注册失败：', e) }
  })
}