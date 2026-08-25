<template>
    <div class="agent-chat">
        <div class="agent-header">
            <van-icon name="chat-o" class="agent-header-icon" />
            <div class="agent-header-title">AI 助手</div>
        </div>

        <div class="agent-messages" ref="msgBox">
            <div class="msg-row ai">
                <div class="msg-bubble ai-bubble">你好，我是智慧景区 AI 助手，可以问我故宫景点、门票、分时预约、天气、退款等问题。</div>
            </div>
            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
                <div class="msg-bubble" :class="m.role + '-bubble'">{{ m.content }}</div>
            </div>
            <div v-if="loading" class="msg-row ai">
                <div class="msg-bubble ai-bubble typing">思考中...</div>
            </div>
        </div>

        <div class="agent-input">
            <van-field v-model="question" placeholder="请输入你的问题" :disabled="loading" @keyup.enter="send" />
            <van-button type="primary" size="small" :loading="loading" @click="send">发送</van-button>
        </div>
    </div>
</template>

<script>
    import { agentChat } from '@/api/agent'
    import { showToast } from 'vant'

    export default {
        name: 'AgentChat',
        data() {
            return { question: '', messages: [], loading: false }
        },
        methods: {
            async send() {
                const q = this.question.trim()
                if (!q || this.loading) return
                this.messages.push({ role: 'user', content: q })
                this.question = ''
                this.loading = true
                this.scrollBottom()
                try {
                    const res = await agentChat(q)
                    this.messages.push({ role: 'ai', content: res.data || '' })
                } catch (e) {
                    this.messages.push({ role: 'ai', content: 'AI 服务暂不可用，请稍后再试。' })
                } finally {
                    this.loading = false
                    this.scrollBottom()
                }
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
    .agent-chat { min-height: 100vh; display: flex; flex-direction: column; background: linear-gradient(160deg, #070b18 0%, #0c1730 55%, #0a1226 100%); }
    .agent-header { display: flex; align-items: center; gap: 8px; padding: 0 16px; height: 50px; background: rgba(10,16,34,0.94); border-bottom: 1px solid rgba(120,170,255,0.16); }
    .agent-header-icon { color: #4da3ff; font-size: 20px; }
    .agent-header-title { font-size: 17px; font-weight: 700; color: #e8eefc; letter-spacing: 2px; }
    .agent-messages { flex: 1; overflow-y: auto; padding: 14px 16px; }
    .msg-row { display: flex; margin-bottom: 12px; }
    .msg-row.user { justify-content: flex-end; }
    .msg-bubble { max-width: 78%; padding: 10px 14px; border-radius: 14px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
    .msg-bubble.user-bubble { background: linear-gradient(135deg, #3a6ec5, #2456a8); color: #fff; border-bottom-right-radius: 4px; }
    .msg-bubble.ai-bubble { background: rgba(16,28,56,0.85); border: 1px solid rgba(120,170,255,0.18); color: #dbe4f5; border-bottom-left-radius: 4px; }
    .msg-bubble.typing { color: #8fa0c2; }
    .agent-input { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: rgba(10,16,34,0.94); border-top: 1px solid rgba(120,170,255,0.16); }
    .agent-input :deep(.van-field) { background: rgba(10,18,38,0.6); border: 1px solid rgba(120,170,255,0.16); border-radius: 10px; }
    .agent-input :deep(.van-field__control) { color: #e8eefc; }
    .agent-input :deep(.van-field__control::placeholder) { color: #5f7399; }
</style>
