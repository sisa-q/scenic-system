import { defineStore } from 'pinia'

/** 全局 AI 悬浮窗状态（跨页面常驻，替代原独立 AI 助手页） */
export const useAgentStore = defineStore('agent', {
    state: () => ({
        visible: false,       // 悬浮窗是否展开（false = 右下角悬浮球）
        messages: [],         // [{ role:'user'|'ai', content, steps:[], actions:[] }]
        loading: false,
        pendingConfirm: null, // 危险操作确认卡 { type, action, params, question, summary }
        confirming: false,
        sessionId: 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8),
        sceneApi: null,       // 首页 3D 场景注册的聚焦能力 { focusSpot(name) }
        unread: 0
    }),
    actions: {
        open() { this.visible = true; this.unread = 0 },
        close() { this.visible = false },
        toggle() { this.visible = !this.visible; if (this.visible) this.unread = 0 },
        pushMsg(m) { this.messages.push(m) },
        setLoading(v) { this.loading = v },
        setConfirm(c) { this.pendingConfirm = c },
        registerSceneApi(api) { this.sceneApi = api },
        reset() {
            this.messages = []
            this.pendingConfirm = null
            this.sessionId = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
        }
    }
})
