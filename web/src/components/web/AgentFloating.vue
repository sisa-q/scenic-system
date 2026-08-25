<template>
    <div class="agent-float">
        <!-- 悬浮球（收起态） -->
        <div v-if="!agentStore.visible" class="agent-bubble" @click="agentStore.toggle()">
            <van-icon name="chat-o" />
            <span v-if="agentStore.unread > 0" class="bubble-badge">{{ agentStore.unread }}</span>
        </div>

        <!-- 悬浮窗（展开态，可拖动） -->
        <div
                v-else
                ref="win"
                class="agent-window"
                :style="winStyle"
                @pointerdown="onPointerDown"
                @pointermove="onPointerMove"
                @pointerup="onPointerUp"
                @pointercancel="onPointerUp"
        >
            <div class="aw-header no-drag">
                <van-icon name="chat-o" class="aw-title-icon" />
                <span class="aw-title">AI 助手</span>
                <span class="aw-sub">可拖动 · 自动操作页面</span>
                <div class="aw-actions">
                    <van-icon name="replay" class="aw-act" title="清空会话" @click="resetChat" />
                    <van-icon name="down" class="aw-act" title="收起" @click="agentStore.toggle()" />
                </div>
            </div>

            <div class="aw-messages" ref="msgBox">
                <div class="aw-welcome">你好，我是智慧景区 AI 助手。可以问我天气、门票、分时预约，也能帮你下单、支付、退款——我会直接调度页面帮你完成操作。</div>
                <div v-for="(m, i) in agentStore.messages" :key="i" class="aw-row" :class="m.role">
                    <div class="aw-bubble" :class="m.role + '-bubble'">
                        <div class="aw-content">{{ m.content }}</div>
                        <div v-if="m.steps && m.steps.length" class="aw-steps">
                            <div v-for="(s, si) in m.steps" :key="si" class="aw-step" :class="s.status">
                                <span class="step-dot"></span>
                                <span class="step-tool">{{ toolLabel(s.tool) }}</span>
                                <span class="step-status">{{ s.status === 'done' ? '完成' : '待确认' }}</span>
                                <span class="step-summary">{{ s.summary }}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div v-if="agentStore.loading" class="aw-row ai">
                    <div class="aw-bubble ai-bubble typing">思考中…</div>
                </div>

                <div v-if="agentStore.pendingConfirm" class="aw-confirm">
                    <div class="aw-confirm-tip">该操作需要你的确认：</div>
                    <div class="aw-confirm-summary">{{ agentStore.pendingConfirm.summary }}</div>
                    <div class="aw-confirm-btns">
                        <van-button size="small" type="primary" :loading="agentStore.confirming" @click="doConfirm">确认</van-button>
                        <van-button size="small" plain @click="cancelConfirm">取消</van-button>
                    </div>
                </div>
            </div>

            <div class="aw-input no-drag">
                <van-field v-model="draft" placeholder="问问天气 / 帮我买票 / 查订单…" :disabled="agentStore.loading || !!agentStore.pendingConfirm" @keyup.enter="send" />
                <van-button type="primary" size="small" :loading="agentStore.loading" @click="send">发送</van-button>
            </div>
        </div>
    </div>
</template>

<script>
    import { agentChat, agentConfirm } from '@/api/agent'
    import { useAgentStore } from '@/store/modules/agent'
    import { executeAgentActions } from '@/utils/agentActions'
    import { showToast } from 'vant'

    const TOOL_LABELS = {
        get_weather: '查询天气',
        get_spots: '查询景点',
        get_policies: '查询票种',
        get_slots: '查询时段',
        get_notices: '查询公告',
        get_my_orders: '查询订单',
        get_order_detail: '查询订单详情',
        place_order: '下单',
        mock_pay: '支付',
        apply_refund: '退款'
    }

    export default {
        name: 'AgentFloating',
        setup() {
            const agentStore = useAgentStore()
            return { agentStore }
        },
        data() {
            return {
                draft: '',
                pos: { x: null, y: null },
                dragging: false,
                startX: 0,
                startY: 0,
                origX: 0,
                origY: 0
            }
        },
        computed: {
            winStyle() {
                const s = {}
                if (this.pos.x !== null) s.left = this.pos.x + 'px'
                if (this.pos.y !== null) s.top = this.pos.y + 'px'
                return s
            }
        },
        watch: {
            'agentStore.messages.length'() { this.scrollBottom() },
            'agentStore.loading'() { this.scrollBottom() },
            'agentStore.pendingConfirm'() { this.scrollBottom() }
        },
        mounted() {
            // 初始位置：右下角（避开底部 TabBar）
            try {
                const saved = JSON.parse(localStorage.getItem('agentFloatPos'))
                if (saved && typeof saved.x === 'number' && typeof saved.y === 'number') {
                    this.pos = saved
                }
            } catch (e) { /* 忽略 */ }
            if (this.pos.x === null) this.pos.x = Math.max(8, window.innerWidth - 336)
            if (this.pos.y === null) this.pos.y = Math.max(8, window.innerHeight - 500)
        },
        methods: {
            toolLabel(t) { return TOOL_LABELS[t] || t },
            scrollBottom() {
                this.$nextTick(() => {
                    const box = this.$refs.msgBox
                    if (box) box.scrollTop = box.scrollHeight
                })
            },
            // ===== 拖动（标题栏/窗口整体；输入区与按钮排除） =====
            onPointerDown(e) {
                if (e.target.closest('.no-drag')) return
                this.dragging = true
                this.startX = e.clientX
                this.startY = e.clientY
                const rect = this.$refs.win.getBoundingClientRect()
                this.origX = rect.left
                this.origY = rect.top
                try { this.$refs.win.setPointerCapture(e.pointerId) } catch (err) { /* 忽略 */ }
            },
            onPointerMove(e) {
                if (!this.dragging) return
                let x = this.origX + (e.clientX - this.startX)
                let y = this.origY + (e.clientY - this.startY)
                const vw = window.innerWidth
                const vh = window.innerHeight
                x = Math.min(Math.max(0, x), vw - 320)
                y = Math.min(Math.max(0, y), vh - 60)
                this.pos.x = x
                this.pos.y = y
            },
            onPointerUp() {
                if (!this.dragging) return
                this.dragging = false
                try { localStorage.setItem('agentFloatPos', JSON.stringify(this.pos)) } catch (e) { /* 忽略 */ }
            },
            // ===== 对话 =====
            resetChat() {
                this.agentStore.reset()
                this.draft = ''
                showToast('已清空会话')
            },
            async send() {
                const q = this.draft.trim()
                if (!q || this.agentStore.loading || this.agentStore.pendingConfirm) return
                this.agentStore.pushMsg({ role: 'user', content: q })
                this.draft = ''
                this.agentStore.setLoading(true)
                try {
                    const res = await agentChat(q, this.agentStore.sessionId)
                    const d = res.data || {}
                    if (d.type === 'confirm') {
                        this.agentStore.setConfirm(d)
                    } else {
                        this.agentStore.pushMsg({ role: 'ai', content: d.content || '', steps: d.steps || [], actions: d.actions || [] })
                        if (d.actions && d.actions.length) executeAgentActions(d.actions)
                    }
                } catch (e) {
                    this.agentStore.pushMsg({ role: 'ai', content: 'AI 服务暂不可用，请稍后再试。', steps: [], actions: [] })
                } finally {
                    this.agentStore.setLoading(false)
                }
            },
            async doConfirm() {
                const c = this.agentStore.pendingConfirm
                if (!c) return
                this.agentStore.confirming = true
                try {
                    const res = await agentConfirm(c.question, c.action, c.params, this.agentStore.sessionId)
                    const d = res.data || {}
                    this.agentStore.pushMsg({ role: 'ai', content: d.content || '', steps: d.steps || [], actions: d.actions || [] })
                    if (d.actions && d.actions.length) executeAgentActions(d.actions)
                } catch (e) {
                    this.agentStore.pushMsg({ role: 'ai', content: 'AI 服务暂不可用，请稍后再试。', steps: [], actions: [] })
                } finally {
                    this.agentStore.setConfirm(null)
                    this.agentStore.confirming = false
                }
            },
            cancelConfirm() {
                this.agentStore.setConfirm(null)
                showToast('已取消')
            }
        }
    }
</script>

<style scoped>
    .agent-bubble {
        position: fixed;
        right: 16px;
        bottom: 96px;
        z-index: 3000;
        width: 54px;
        height: 54px;
        border-radius: 50%;
        background: linear-gradient(135deg, #3a6ec5, #2456a8);
        color: #fff;
        font-size: 26px;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 6px 18px rgba(36, 86, 168, 0.55);
        cursor: pointer;
        user-select: none;
    }
    .bubble-badge {
        position: absolute;
        top: -4px;
        right: -4px;
        min-width: 18px;
        height: 18px;
        border-radius: 9px;
        background: #ee0a24;
        color: #fff;
        font-size: 11px;
        line-height: 18px;
        text-align: center;
        padding: 0 4px;
    }
    .agent-window {
        position: fixed;
        z-index: 3000;
        width: 320px;
        max-width: 92vw;
        height: 480px;
        max-height: 86vh;
        display: flex;
        flex-direction: column;
        border-radius: 14px;
        overflow: hidden;
        background: linear-gradient(160deg, #0a1020 0%, #0c1730 60%, #0a1226 100%);
        border: 1px solid rgba(120, 170, 255, 0.22);
        box-shadow: 0 12px 40px rgba(0, 0, 0, 0.55);
        touch-action: none;
    }
    .aw-header {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 0 12px;
        height: 44px;
        background: rgba(12, 20, 40, 0.96);
        border-bottom: 1px solid rgba(120, 170, 255, 0.16);
        cursor: grab;
    }
    .aw-title-icon { color: #4da3ff; font-size: 18px; }
    .aw-title { font-size: 15px; font-weight: 700; color: #e8eefc; }
    .aw-sub { font-size: 11px; color: #5f7399; margin-left: 4px; }
    .aw-actions { margin-left: auto; display: flex; gap: 10px; }
    .aw-act { color: #8fa0c2; font-size: 16px; cursor: pointer; }
    .aw-act:hover { color: #4da3ff; }
    .aw-messages {
        flex: 1;
        overflow-y: auto;
        padding: 12px;
        background: rgba(8, 14, 28, 0.55);
    }
    .aw-welcome {
        font-size: 12px;
        line-height: 1.7;
        color: #8fa0c2;
        background: rgba(16, 28, 56, 0.6);
        border: 1px solid rgba(120, 170, 255, 0.14);
        border-radius: 10px;
        padding: 8px 10px;
        margin-bottom: 10px;
    }
    .aw-row { display: flex; margin-bottom: 10px; }
    .aw-row.user { justify-content: flex-end; }
    .aw-bubble { max-width: 88%; padding: 8px 11px; border-radius: 12px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
    .aw-bubble.user-bubble { background: linear-gradient(135deg, #3a6ec5, #2456a8); color: #fff; border-bottom-right-radius: 4px; }
    .aw-bubble.ai-bubble { background: rgba(16, 28, 56, 0.85); border: 1px solid rgba(120, 170, 255, 0.18); color: #dbe4f5; border-bottom-left-radius: 4px; }
    .aw-bubble.typing { color: #8fa0c2; }
    .aw-steps { margin-top: 8px; border-top: 1px dashed rgba(120, 170, 255, 0.2); padding-top: 6px; }
    .aw-step { display: flex; align-items: center; gap: 5px; font-size: 11px; line-height: 1.8; color: #9fb2d4; }
    .aw-step .step-dot { width: 6px; height: 6px; border-radius: 50%; background: #5f7399; flex: none; }
    .aw-step.done .step-dot { background: #2fb56b; }
    .aw-step.need_confirm .step-dot { background: #f5c25c; }
    .aw-step .step-tool { color: #4da3ff; flex: none; }
    .aw-step .step-status { flex: none; }
    .aw-step.done .step-status { color: #2fb56b; }
    .aw-step.need_confirm .step-status { color: #f5c25c; }
    .aw-step .step-summary { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .aw-confirm { margin: 8px 0; padding: 10px 12px; border-radius: 10px; background: rgba(255, 193, 7, 0.10); border: 1px solid rgba(255, 193, 7, 0.35); }
    .aw-confirm-tip { font-size: 12px; color: #f5c25c; margin-bottom: 4px; }
    .aw-confirm-summary { font-size: 13px; color: #e8eefc; line-height: 1.6; }
    .aw-confirm-btns { display: flex; gap: 8px; margin-top: 8px; }
    .aw-input { display: flex; align-items: center; gap: 6px; padding: 8px 10px; background: rgba(10, 16, 34, 0.94); border-top: 1px solid rgba(120, 170, 255, 0.16); }
    .aw-input :deep(.van-field) { background: rgba(10, 18, 38, 0.6); border: 1px solid rgba(120, 170, 255, 0.16); border-radius: 10px; }
    .aw-input :deep(.van-field__control) { color: #e8eefc; font-size: 13px; }
    .aw-input :deep(.van-field__control::placeholder) { color: #5f7399; }
</style>
