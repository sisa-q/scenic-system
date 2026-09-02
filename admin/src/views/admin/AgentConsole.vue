<template>
    <div class="admin-page agent-console">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">AI 智能体控制台</div>
                <div class="page-subtitle">游客服务 / 运营决策 / 应急调度 —— 三职能共享同一模型，意图路由串行调度</div>
            </div>
            <div class="page-header-right role-switch">
                <el-radio-group v-model="role" @change="onRoleChange" size="default">
                    <el-radio-button label="tourist">游客服务</el-radio-button>
                    <el-radio-button label="ops">运营决策</el-radio-button>
                    <el-radio-button label="emergency">应急调度</el-radio-button>
                </el-radio-group>
            </div>
        </div>

        <div class="role-tip">
            {{ roleTip }}
        </div>

        <div class="agent-body">
            <div class="agent-messages" ref="msgBox">
                <div class="msg-row ai">
                    <div class="msg-bubble ai-bubble">{{ roleWelcome }}</div>
                </div>
                <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
                    <div class="msg-bubble" :class="m.role + '-bubble'">{{ m.content }}</div>
                </div>
                <div v-if="lastSteps && lastSteps.length" class="step-box">
                    <div class="step-title">工具执行步骤</div>
                    <div v-for="(s, k) in lastSteps" :key="k" class="step-row">
                        <span class="step-tool">{{ s.tool }}</span>
                        <span class="step-status" :class="s.status">{{ s.status }}</span>
                        <span class="step-summary">{{ s.summary }}</span>
                    </div>
                </div>
                <div v-if="loading" class="msg-row ai">
                    <div class="msg-bubble ai-bubble typing">思考中...</div>
                </div>

                <div v-if="pendingConfirm" class="confirm-card">
                    <div class="confirm-tip">⚠️ 该操作需要管理员确认：</div>
                    <div class="confirm-summary">{{ pendingConfirm.summary }}</div>
                    <div class="confirm-btns">
                        <el-button type="primary" :loading="confirming" @click="doConfirm">确认执行</el-button>
                        <el-button plain @click="cancelConfirm">取消</el-button>
                    </div>
                </div>
            </div>

            <div class="agent-input">
                <el-input
                        v-model="question"
                        placeholder="输入问题，回车发送（运营/应急会调用国庆 10 天模拟数据分析工具）"
                        :disabled="loading || !!pendingConfirm"
                        clearable
                        @keyup.enter="send"
                />
                <el-button type="primary" :loading="loading" @click="send">发送</el-button>
            </div>
        </div>
    </div>
</template>

<script>
    import { agentChat, agentConfirm } from '@/api/agent'
    import { ElMessage } from 'element-plus'

    const ROLE_INFO = {
        tourist: {
            tip: '游客服务：回答游客问答、购票引导；支持查天气/景点/票种/时段/我的订单，下单/支付/退款需确认。',
            welcome: '你好，我是游客服务智能体。可以问故宫景点、门票、分时预约、天气，也能帮你查订单、下单、支付、退款。'
        },
        ops: {
            tip: '运营决策：基于国庆 10 天（2026-09-28 ~ 10-07）模拟数据，分析客流/销售/时段/退款并给出运营建议。',
            welcome: '你好，我是运营决策智能体。可以分析国庆假期客流、销售、时段预约率、退款情况并给出运营建议。'
        },
        emergency: {
            tip: '应急调度：扫描承载率四区/时段售罄/天气/退款激增等异常并给处置建议；发布公告、加开时段为写操作，需管理员确认。',
            welcome: '你好，我是应急调度智能体。可以扫描国庆期间异常态势并给出处置建议，需要时可发布公告或加开时段。'
        }
    }

    export default {
        name: 'AgentConsole',
        data() {
            return {
                role: 'ops',
                question: '',
                messages: [],
                lastSteps: [],
                loading: false,
                pendingConfirm: null,
                confirming: false,
                sessionId: 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
            }
        },
        computed: {
            roleInfo() {
                return ROLE_INFO[this.role] || ROLE_INFO.tourist
            },
            roleTip() {
                return this.roleInfo.tip
            },
            roleWelcome() {
                return this.roleInfo.welcome
            }
        },
        methods: {
            onRoleChange() {
                // 切换角色：重置会话与消息，避免串角色记忆
                this.sessionId = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
                this.messages = []
                this.lastSteps = []
                this.pendingConfirm = null
            },
            async send() {
                const q = this.question.trim()
                if (!q || this.loading || this.pendingConfirm) return
                this.messages.push({ role: 'user', content: q })
                this.question = ''
                this.lastSteps = []
                this.loading = true
                this.scrollBottom()
                try {
                    const res = await agentChat(q, this.sessionId, this.role)
                    const d = (res && res.data) || {}
                    if (d.type === 'confirm') {
                        this.pendingConfirm = d
                        this.lastSteps = d.steps || []
                    } else {
                        this.messages.push({ role: 'ai', content: d.content || '' })
                        this.lastSteps = d.steps || []
                    }
                } catch (e) {
                    this.messages.push({ role: 'ai', content: 'AI 服务暂不可用，请稍后再试。' })
                } finally {
                    this.loading = false
                    this.scrollBottom()
                }
            },
            async doConfirm() {
                if (!this.pendingConfirm) return
                this.confirming = true
                try {
                    const res = await agentConfirm(
                        this.pendingConfirm.question,
                        this.pendingConfirm.action,
                        this.pendingConfirm.params,
                        this.sessionId,
                        this.role
                    )
                    const d = (res && res.data) || {}
                    this.messages.push({ role: 'ai', content: d.content || '' })
                    this.lastSteps = d.steps || []
                } catch (e) {
                    this.messages.push({ role: 'ai', content: 'AI 服务暂不可用，请稍后再试。' })
                } finally {
                    this.pendingConfirm = null
                    this.confirming = false
                    this.scrollBottom()
                }
            },
            cancelConfirm() {
                this.pendingConfirm = null
                ElMessage.info('已取消')
            },
            scrollBottom() {
                this.$nextTick(() => {
                    const box = this.$refs.msgBox
                    if (box) box.scrollTop = box.scrollHeight
                })
            }
        }
    }
</script>

<style scoped>
    .agent-console { display: flex; flex-direction: column; gap: 12px; height: 100%; }
    .page-header-right.role-switch { display: flex; align-items: center; }
    .role-switch :deep(.el-radio-button__inner) { background: rgba(16,28,56,0.6); color: #aebcd8; border-color: rgba(120,170,255,0.2); }
    .role-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { background: linear-gradient(135deg,#3a6ec5,#2456a8); color: #fff; border-color: #3a6ec5; }
    .role-tip { padding: 8px 14px; border-radius: 10px; background: rgba(16,28,56,0.55); border: 1px solid rgba(120,170,255,0.16); color: #8fa5c8; font-size: 13px; line-height: 1.6; flex-shrink: 0; }
    .agent-body { flex: 1; min-height: 460px; display: flex; flex-direction: column; background: rgba(10,15,32,0.55); border: 1px solid rgba(120,170,255,0.16); border-radius: 14px; overflow: hidden; }
    .agent-messages { flex: 1; overflow-y: auto; padding: 16px; }
    .msg-row { display: flex; margin-bottom: 12px; }
    .msg-row.user { justify-content: flex-end; }
    .msg-bubble { max-width: 82%; padding: 10px 14px; border-radius: 14px; font-size: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
    .msg-bubble.user-bubble { background: linear-gradient(135deg,#3a6ec5,#2456a8); color: #fff; border-bottom-right-radius: 4px; }
    .msg-bubble.ai-bubble { background: rgba(16,28,56,0.85); border: 1px solid rgba(120,170,255,0.18); color: #dbe4f5; border-bottom-left-radius: 4px; }
    .msg-bubble.typing { color: #8fa0c2; }
    .step-box { margin: 6px 0 12px; padding: 10px 14px; border-radius: 10px; background: rgba(9,16,38,0.6); border: 1px solid rgba(120,170,255,0.14); }
    .step-title { font-size: 12px; color: #7fa0c8; margin-bottom: 6px; }
    .step-row { display: flex; align-items: baseline; gap: 10px; font-size: 12px; line-height: 1.6; color: #9fb4d4; }
    .step-tool { color: #4da3ff; font-family: Consolas,Menlo,monospace; flex-shrink: 0; }
    .step-status { flex-shrink: 0; }
    .step-status.done { color: #4dffa6; }
    .step-status.need_confirm { color: #ffd76a; }
    .step-status.error { color: #ff5f6d; }
    .step-summary { color: #7d8db0; word-break: break-all; }
    .confirm-card { margin: 8px 0; padding: 12px 14px; border-radius: 12px; background: rgba(255,193,7,0.10); border: 1px solid rgba(255,193,7,0.35); }
    .confirm-tip { font-size: 13px; color: #f5c25c; margin-bottom: 6px; }
    .confirm-summary { font-size: 14px; color: #e8eefc; line-height: 1.6; }
    .confirm-btns { display: flex; gap: 10px; margin-top: 10px; }
    .agent-input { display: flex; align-items: center; gap: 10px; padding: 12px; border-top: 1px solid rgba(120,170,255,0.16); background: rgba(10,16,34,0.9); }
    .agent-input :deep(.el-input__wrapper) { background: rgba(10,18,38,0.7); box-shadow: 0 0 0 1px rgba(120,170,255,0.2) inset; }
    .agent-input :deep(.el-input__inner) { color: #e8eefc; }
</style>
