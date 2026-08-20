<template>
    <div class="order-detail-page">
        <!-- 订单信息卡片 -->
        <div class="order-card">
            <div class="order-header">
                <span class="order-no">订单号：{{ order.orderNo }}</span>
                <span class="order-status" :style="{ color: statusColor }">{{ statusText }}</span>
            </div>
            <div class="order-body">
                <div class="info-row"><label>景点</label><span>{{ order.spotName }}</span></div>
                <div class="info-row"><label>票种</label><span>{{ order.ticketType }}</span></div>
                <div class="info-row"><label>时段</label><span>{{ order.timeSlot }}</span></div>
                <div class="info-row"><label>数量</label><span>×{{ order.quantity }}</span></div>
                <div class="info-row"><label>单价</label><span>¥{{ order.unitPrice }}</span></div>
                <div class="info-row"><label>总金额</label><span class="total-price">¥{{ order.totalAmount }}</span></div>
                <div class="info-row"><label>下单时间</label><span>{{ order.createTime }}</span></div>
            </div>
            <!-- 待支付倒计时 / 已失效提示 -->
            <div class="pay-timer" v-if="isPending && !isExpired">
                <span class="pay-timer-label">⏰ 支付剩余时间</span>
                <span class="pay-timer-count">{{ countdownText }}</span>
            </div>
            <div class="pay-timer pay-expired" v-else-if="isExpired">
                <span class="pay-timer-label">⚠ 订单已失效</span>
            </div>
            <div class="pay-timer pay-refund-pending" v-else-if="order.status === 5">
                <span class="pay-timer-label">⏳ 退款申请处理中，请等待景区审核</span>
            </div>

            <div class="order-actions">
                <van-button
                        v-if="canPay"
                        type="danger"
                        size="small"
                        @click="goPay"
                >立即支付</van-button>
                <van-button
                        v-if="canEvaluate"
                        type="primary"
                        size="small"
                        @click="showEvaluate = true"
                >去评价</van-button>
                <van-button
                        v-if="canRefund"
                        type="danger"
                        size="small"
                        plain
                        @click="applyRefund"
                        :loading="refunding"
                >申请退款</van-button>
                <van-button
                        v-if="canCancelRefund"
                        type="default"
                        size="small"
                        plain
                        @click="cancelRefundRequest"
                        :loading="cancelingRefund"
                >取消退款申请</van-button>
            </div>
        </div>

        <!-- 评价区域 -->
        <div class="evaluation-section" v-if="hasEvaluated || showEvaluate">
            <h3>📝 我的评价</h3>
            <div v-if="hasEvaluated && !showEvaluate" class="evaluation-display">
                <div class="eval-rating">
                    <StarRating :value="evaluation.rating ?? evaluation.score ?? 0" disabled />
                    <span class="eval-score-text">{{ evaluation.rating ?? evaluation.score ?? 0 }} 星</span>
                </div>
                <p class="eval-content">{{ evaluation.content }}</p>
                <div class="eval-extra">
                    <span class="emotion-score">情感得分：{{ evaluation.emotionScore ?? 0 }}</span>
                    <span class="eval-time">{{ evaluation.createTime }}</span>
                </div>
                <van-button size="small" plain type="primary" @click="startEdit">修改评价</van-button>
            </div>

            <div v-else class="evaluation-form">
                <div class="form-group">
                    <label>评分：</label>
                    <StarRating v-model="form.score" />
                </div>
                <div class="form-group">
                    <label>评价内容：</label>
                    <van-field
                            v-model="form.content"
                            type="textarea"
                            rows="4"
                            placeholder="请分享您的游览体验..."
                            maxlength="200"
                            show-word-limit
                    />
                </div>
                <div class="form-actions">
                    <van-button type="primary" @click="submitEvaluation" :loading="submitting">
                        {{ hasEvaluated ? '更新评价' : '提交评价' }}
                    </van-button>
                    <van-button v-if="hasEvaluated" plain @click="cancelEdit">取消</van-button>
                </div>
            </div>
        </div>

        <TabBar />
    </div>
</template>

<script>
    import TabBar from '@/components/web/TabBar.vue'
    import { showToast, showConfirmDialog } from 'vant'
    import { getOrderDetail, applyRefund, cancelRefund } from '@/api/order'
    import { submitEvaluation, updateEvaluation, getOrderEvaluation } from '@/api/evaluation'
    import StarRating from '@/components/web/StarRating.vue'
    import { getTokenRole } from '@/utils/auth'

    export default {
        name: 'OrderDetail',
        components: { TabBar, StarRating },
        data() {
            return {
                // 订单数据（通过 API 获取）
                order: {
                    orderNo: '',
                    status: 0,
                    statusText: '',
                    spotName: '',
                    ticketType: '',
                    timeSlot: '',
                    quantity: 0,
                    unitPrice: 0,
                    totalAmount: 0,
                    createTime: ''
                },
                hasEvaluated: false,
                evaluation: null,
                showEvaluate: false,
                form: { content: '', score: 5 },
                submitting: false,
                refunding: false,
                cancelingRefund: false,
                isEditMode: false,
                // 待支付倒计时（限时 30 分钟，与后端 pay.pay-timeout-minutes 一致）
                remainSeconds: 30 * 60,
                countdownTimer: null,
                refreshTimer: null
            }
        },
        computed: {
            // 超时的待支付订单视为已失效（状态 4）
            effectiveStatus() {
                if (this.order.status === 0 && this.remainSeconds <= 0) return 4
                return this.order.status
            },
            isPending() {
                return this.order.status === 0
            },
            isExpired() {
                return this.effectiveStatus === 4
            },
            canPay() {
                // 停用景点/时段的待支付订单不可支付
                return this.order.status === 0 && this.remainSeconds > 0 && !this.order.disabled
            },
            countdownText() {
                const m = Math.floor(this.remainSeconds / 60)
                const s = this.remainSeconds % 60
                return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
            },
            statusColor() {
                // 已停用待退款 / 申请退款中 红色
                if (this.order.status === 5) return '#ee0a24'
                if (this.order.status === 1 && this.order.disabled) return '#ee0a24'
                if (this.order.status === 0 && this.order.disabled) return '#faad14'
                const map = ['#faad14', '#ff976a', '#07c160', '#ee0a24', '#969799']
                return map[this.effectiveStatus] || '#333'
            },
            statusText() {
                if (this.effectiveStatus === 4) return '已失效'
                // 已停用状态（景点/时段停用）
                if (this.order.status === 0 && this.order.disabled) return '待支付已停用'
                if (this.order.status === 1 && this.order.disabled) return '已支付已停用待退款'
                if (this.order.status === 5) return '申请退款中'
                const map = ['待支付', '已支付', '已使用', '已退款', '已失效']
                return map[this.order.status] || '未知'
            },
            canEvaluate() {
                return this.order.status === 2 && !this.hasEvaluated && !this.showEvaluate
            },
            canRefund() {
                // 已支付订单可申请退款（一次性，申请后进入“退款申请中”）
                // 管理员角色不展示申请退款按钮，退款请到管理端订单管理点击“退款”
                return this.order.status === 1 && getTokenRole() !== 'admin'
            },
            canCancelRefund() {
                // 退款申请中的订单可取消申请
                return this.order.status === 5
            }
        },
        async mounted() {
            const orderId = this.$route.params.id || this.$route.query.id
            if (!orderId) {
                showToast('订单不存在')
                this.$router.back()
                return
            }
            await this.fetchOrderDetail(orderId)
            await this.fetchEvaluation(orderId)
            // 实时同步：每 3 秒静默刷新订单状态（支付/退款状态变更即时可见）
            this.startAutoRefresh()
            document.addEventListener('visibilitychange', this.onVisibilityChange)
        },
        beforeUnmount() {
            if (this.countdownTimer) {
                clearInterval(this.countdownTimer)
                this.countdownTimer = null
            }
            // 页面卸载时停止轮询，避免资源泄漏
            this.stopAutoRefresh()
            document.removeEventListener('visibilitychange', this.onVisibilityChange)
        },
        methods: {
            // 获取订单详情（真实 API）
            async fetchOrderDetail(orderId, silent = false) {
                try {
                    const res = await getOrderDetail(orderId)
                    const d = res.data || {}
                    this.order = {
                        ...d,
                        ticketType: d.policyName || '',
                        timeSlot: d.startTime ? d.startTime + ' - ' + d.endTime : '',
                        unitPrice: d.policyPrice || 0
                    }
                    // 待支付订单启动 2 分钟倒计时
                    if (d.status === 0) {
                        this.startCountdown()
                    } else {
                        this.stopCountdown()
                    }
                } catch (e) {
                    console.error('获取订单详情失败', e)
                    if (!silent) showToast('获取订单详情失败')
                }
            },
            // 获取评价
            // ====== 实时同步：自动轮询 ======
            startAutoRefresh() {
                this.stopAutoRefresh()
                const orderId = this.$route.params.id || this.$route.query.id
                this.refreshTimer = setInterval(() => {
                    if (orderId) this.fetchOrderDetail(orderId, true)
                }, 3000)
            },
            stopAutoRefresh() {
                if (this.refreshTimer) {
                    clearInterval(this.refreshTimer)
                    this.refreshTimer = null
                }
            },
            onVisibilityChange() {
                if (!document.hidden) {
                    const orderId = this.$route.params.id || this.$route.query.id
                    if (orderId) this.fetchOrderDetail(orderId, true)
                }
            },
            async fetchEvaluation(orderId) {
                try {
                    const res = await getOrderEvaluation(orderId)
                    if (res.data) {
                        this.hasEvaluated = true
                        this.evaluation = res.data
                        this.form.content = res.data.content || ''
                        this.form.score = res.data.score || res.data.rating || 5
                    }
                } catch (e) {
                    console.error('获取评价失败', e)
                }
            },
            startEdit() {
                this.isEditMode = true
                this.showEvaluate = true
            },
            cancelEdit() {
                this.showEvaluate = false
                this.isEditMode = false
                if (this.hasEvaluated) {
                    this.form.content = this.evaluation.content || ''
                    this.form.score = this.evaluation.score || this.evaluation.rating || 5
                } else {
                    this.form.content = ''
                    this.form.score = 5
                }
            },
            async submitEvaluation() {
                if (!this.form.content.trim()) {
                    showToast('请填写评价内容')
                    return
                }

                this.submitting = true
                const orderId = this.$route.params.id || this.$route.query.id
                try {
                    const payload = {
                        orderId,
                        content: this.form.content.trim(),
                        score: this.form.score,
                        orderNo: this.order.orderNo,
                        spotName: this.order.spotName
                    }

                    let res
                    if (this.hasEvaluated && this.isEditMode) {
                        res = await updateEvaluation({ ...payload, id: this.evaluation.id })
                        showToast('评价已更新')
                    } else {
                        res = await submitEvaluation(payload)
                        showToast('评价提交成功')
                        this.hasEvaluated = true
                    }

                    // 更新本地数据
                    this.evaluation = {
                        id: res.data?.id || Date.now(),
                        content: payload.content,
                        emotionScore: res.data?.emotionScore ?? 0,
                        createTime: res.data?.createTime || new Date().toLocaleString()
                    }

                    this.showEvaluate = false
                    this.isEditMode = false
                } catch (error) {
                    showToast(error.msg || error.message || '操作失败，请重试')
                } finally {
                    this.submitting = false
                }
            },
            // ====== 待支付倒计时 / 支付 ======
            startCountdown() {
                this.stopCountdown()
                const createTime = this.order.createTime
                if (!createTime) return
                const created = new Date(String(createTime).replace(/-/g, '/')).getTime()
                if (isNaN(created)) return
                const deadline = created + 30 * 60 * 1000
                const update = () => {
                    const remain = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
                    this.remainSeconds = remain
                    if (remain <= 0) {
                        this.stopCountdown()
                    }
                }
                update()
                this.countdownTimer = setInterval(update, 1000)
            },
            stopCountdown() {
                if (this.countdownTimer) {
                    clearInterval(this.countdownTimer)
                    this.countdownTimer = null
                }
            },
            goPay() {
                this.$router.push({ path: '/pay', query: { orderId: this.order.id } })
            },

            // 取消退款申请
            async cancelRefundRequest() {
                const orderId = this.$route.params.id || this.$route.query.id
                try {
                    await showConfirmDialog({
                        title: '取消退款申请',
                        message: '确认取消该订单的退款申请吗？取消后订单将恢复为已支付。'
                    })
                    this.cancelingRefund = true
                    await cancelRefund(orderId)
                    showToast('已取消退款申请')
                    await this.fetchOrderDetail(orderId)
                } catch (e) {
                    if (e !== 'cancel' && e?.msg) {
                        showToast(e.msg)
                    } else if (e !== 'cancel') {
                        showToast(e.message || '取消失败')
                    }
                } finally {
                    this.cancelingRefund = false
                }
            },

            async applyRefund() {
                const orderId = this.$route.params.id || this.$route.query.id
                try {
                    await showConfirmDialog({
                        title: '申请退款',
                        message: '确认要提交退款申请吗？提交后需等待景区审核，审核通过后才会退款并释放预约数。'
                    })
                    this.refunding = true
                    await applyRefund(orderId)
                    showToast('退款申请已提交，请等待景区审核')
                    await this.fetchOrderDetail(orderId)
                } catch (e) {
                    if (e !== 'cancel' && e?.msg) {
                        showToast(e.msg)
                    } else if (e !== 'cancel') {
                        showToast(e.message || '退款失败')
                    }
                } finally {
                    this.refunding = false
                }
            }
        }
    }
</script>

<style scoped>
    .order-detail-page {
        background: #f5f6fa;
        min-height: 100vh;
        padding: 16px 16px 70px;
    }
    .order-card {
        background: #fff;
        border-radius: 12px;
        padding: 16px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.06);
        margin-bottom: 16px;
    }
    .order-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-bottom: 12px;
        border-bottom: 1px solid #eee;
    }
    .order-no {
        font-weight: 500;
        font-size: 15px;
        color: #323233;
    }
    .order-status {
        font-weight: 600;
        font-size: 16px;
    }
    .order-body {
        padding-top: 12px;
    }
    .info-row {
        display: flex;
        padding: 6px 0;
        font-size: 14px;
        color: #646566;
    }
    .info-row label {
        width: 70px;
        color: #969799;
    }
    .info-row span {
        flex: 1;
        color: #323233;
    }
    .total-price {
        font-weight: 600;
        color: #ee0a24;
        font-size: 16px;
    }
    .pay-timer {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 12px;
        padding: 10px 12px;
        border-radius: 10px;
        background: #fff7e6;
        border: 1px solid #ffd591;
    }
    .pay-timer-label {
        font-size: 13px;
        color: #ad6800;
    }
    .pay-timer-count {
        font-size: 18px;
        font-weight: 700;
        color: #ee0a24;
        font-variant-numeric: tabular-nums;
        letter-spacing: 2px;
    }
    .pay-timer.pay-expired {
        background: #f5f5f5;
        border-color: #d9d9d9;
    }
    .pay-timer.pay-expired .pay-timer-label {
        color: #969799;
        font-weight: 600;
    }
    .pay-timer.pay-refund-pending {
        background: #fff1f0;
        border-color: #ffa39e;
    }
    .pay-timer.pay-refund-pending .pay-timer-label {
        color: #cf1322;
        font-weight: 600;
    }
    .order-actions {
        margin-top: 12px;
        display: flex;
        justify-content: flex-end;
        gap: 8px;
    }
    .evaluation-section {
        background: #fff;
        border-radius: 12px;
        padding: 16px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    }
    .evaluation-section h3 {
        margin: 0 0 12px;
        font-size: 16px;
        color: #323233;
    }
    .evaluation-display {
        border: 1px solid #f0f0f0;
        border-radius: 8px;
        padding: 12px;
    }
    .eval-rating {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
    }
    .eval-score-text {
        color: #f5a623;
        font-weight: 600;
        font-size: 14px;
    }
    .eval-content {
        margin: 0 0 8px 0;
        font-size: 15px;
        line-height: 1.6;
        color: #323233;
    }
    .eval-extra {
        display: flex;
        justify-content: space-between;
        font-size: 13px;
        color: #969799;
    }
    .emotion-score {
        color: #07c160;
        font-weight: 500;
    }
    .eval-time {
        color: #c8c9cc;
    }
    .evaluation-form {
        margin-top: 4px;
    }
    .form-group {
        margin-bottom: 16px;
    }
    .form-group label {
        display: block;
        font-size: 14px;
        color: #646566;
        margin-bottom: 6px;
    }
    .form-actions {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 8px;
    }
</style>
