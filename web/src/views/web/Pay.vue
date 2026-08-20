<template>
    <div class="pay-page">
        <!-- ===== 支付成功页（5 秒后自动跳转订单页） ===== -->
        <div v-if="paid" class="pay-card success-card">
            <div class="success-ring">
                <svg viewBox="0 0 52 52" class="checkmark">
                    <circle cx="26" cy="26" r="24" fill="none" class="checkmark-circle" />
                    <path fill="none" class="checkmark-check" d="M14 27l8 8 16-16" />
                </svg>
            </div>
            <div class="success-title">支付成功</div>
            <div class="success-amount">￥{{ amountText }}</div>
            <div class="success-sub">订单号：{{ orderNo }}</div>
            <div class="success-tip">
                <span class="tip-dot"></span>
                <span>{{ countdown }} 秒后自动跳转到订单页…</span>
            </div>
            <van-button type="primary" block round @click="goOrders">立即查看订单</van-button>
        </div>

        <!-- ===== 支付页 ===== -->
        <div v-else class="pay-card">
            <div class="pay-title">订单支付</div>
            <div class="pay-row"><span>订单号</span><span>{{ orderNo || (returning ? '确认中…' : '') }}</span></div>
            <div class="pay-row"><span>应付金额</span><span class="amount">￥{{ amountText }}</span></div>

            <!-- 支付方式选择：模拟支付 / 支付宝沙箱支付（两模式独立） -->
            <div class="pay-methods">
                <div class="pay-method" :class="{ active: mode === 'mock' }" @click="selectMode('mock')">
                    <div class="method-title">模拟支付</div>
                    <div class="method-desc">本地演示，不调用支付宝，直接确认支付</div>
                </div>
                <div class="pay-method" :class="{ active: mode === 'alipay' }" @click="selectMode('alipay')">
                    <div class="method-title">支付宝沙箱支付</div>
                    <div class="method-desc">跳转支付宝沙箱收银台完成支付</div>
                </div>
            </div>

            <van-button
                    :type="mode === 'alipay' ? 'primary' : 'success'"
                    block
                    round
                    :loading="paying"
                    @click="confirmPay"
            >
                {{ mode === 'alipay' ? '去支付宝收银台支付' : '模拟支付成功' }}
            </van-button>
            <van-button plain block class="cancel-btn" @click="goBack">返回</van-button>
        </div>
    </div>
</template>

<script>
    import { payOrder, mockConfirmPay, alipayReturn, getOrderDetail } from '@/api/order'
    import { showToast } from 'vant'

    export default {
        name: 'Pay',
        data() {
            return {
                orderId: '',
                spotId: '',
                orderNo: '',
                amountText: '0.00',
                // 支付模式：mock=模拟支付 / alipay=支付宝沙箱支付
                mode: 'mock',
                paying: false,
                returning: false, // 正在确认支付宝支付结果并跳转
                paid: false,      // 支付成功，进入 5 秒倒计时
                countdown: 5,
                countdownTimer: null
            }
        },
        mounted() {
            const q = this.$route.query
            this.orderId = q.orderId
            this.spotId = q.spotId || ''
            // 支付宝支付后（return_url 场景，可选）带 out_trade_no 跳回时，验签+查单确认
            if (q.out_trade_no) {
                this.returning = true
                this.confirmReturn(q)
                return
            }
            if (this.orderId) {
                this.fetchOrder()
            } else {
                showToast('订单信息缺失')
                this.$router.back()
            }
        },
        beforeUnmount() {
            this.stopCountdown()
        },
        methods: {
            selectMode(m) {
                this.mode = m
            },
            async fetchOrder() {
                try {
                    const res = await getOrderDetail(this.orderId)
                    const d = res.data || {}
                    this.orderNo = d.orderNo
                    this.amountText = Number(d.totalAmount || 0).toFixed(2)
                    // 已是已支付状态（如通知已确认/已在收银台付过款）：进入成功倒计时
                    if (d.status === 1) {
                        this.showPaidSuccess()
                    }
                } catch (e) {
                    showToast('获取订单信息失败')
                }
            },
            showPaidSuccess() {
                this.paid = true
                this.countdown = 5
                this.stopCountdown()
                this.countdownTimer = setInterval(() => {
                    this.countdown--
                    if (this.countdown <= 0) {
                        this.stopCountdown()
                        this.goOrders()
                    }
                }, 1000)
            },
            stopCountdown() {
                if (this.countdownTimer) {
                    clearInterval(this.countdownTimer)
                    this.countdownTimer = null
                }
            },
            goOrders() {
                this.$router.replace({ path: '/orders', query: { refreshed: Date.now(), status: 1 } })
            },
            goBack() {
                if (this.spotId) {
                    this.$router.replace('/spot/' + this.spotId)
                } else {
                    this.$router.back()
                }
            },
            // 支付宝同步跳转兜底确认（当前未配置 return_url 时不会触发）
            async confirmReturn(q) {
                this.paying = true
                try {
                    const res = await alipayReturn(q)
                    showToast('支付成功')
                    this.showPaidSuccess()
                } catch (e) {
                    showToast(e.msg || e.message || '支付结果确认中，可稍后在订单列表查看')
                    this.returning = false
                    if (this.orderId) {
                        this.fetchOrder()
                    }
                } finally {
                    this.paying = false
                }
            },
            async confirmPay() {
                if (this.mode === 'alipay') {
                    await this.pay()
                } else {
                    await this.mockPay()
                }
            },
            // 支付宝沙箱支付：生成收银台链接并跳转
            async pay() {
                this.paying = true
                try {
                    const res = await payOrder(this.orderId, 'alipay')
                    const d = res.data || {}
                    if (d.type === 'alipay' && d.redirectUrl) {
                        window.location.href = d.redirectUrl
                        return
                    }
                    showToast('未生成支付宝支付链接，请重试')
                } catch (e) {
                    showToast(e.msg || e.message || '支付失败，请重试')
                } finally {
                    this.paying = false
                }
            },
            // 模拟支付：直接确认订单（并同步沙箱镜像余额）
            async mockPay() {
                this.paying = true
                try {
                    await mockConfirmPay(this.orderId)
                    showToast('支付成功')
                    this.showPaidSuccess()
                } catch (e) {
                    showToast(e.msg || e.message || '确认失败')
                } finally {
                    this.paying = false
                }
            }
        }
    }
</script>

<style scoped>
    .pay-page {
        min-height: 100vh;
        box-sizing: border-box;
        padding: 16px;
        display: flex;
        align-items: flex-start;
        justify-content: center;
        /* 摄影艺术 + 科幻智能：深邃蓝紫渐变 + 光晕 */
        background:
            radial-gradient(1200px 600px at 20% -10%, rgba(64, 156, 255, 0.25), transparent 60%),
            radial-gradient(900px 500px at 100% 10%, rgba(140, 90, 255, 0.22), transparent 55%),
            linear-gradient(160deg, #070b18 0%, #0c1730 55%, #0a1226 100%);
    }
    .pay-card {
        width: 100%;
        max-width: 420px;
        margin-top: 6vh;
        border-radius: 18px;
        padding: 24px 18px;
        color: #e8eefc;
        /* 玻璃拟态 */
        background: rgba(16, 28, 56, 0.72);
        border: 1px solid rgba(120, 170, 255, 0.18);
        box-shadow: 0 18px 50px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.06);
        backdrop-filter: blur(14px);
        -webkit-backdrop-filter: blur(14px);
    }
    .pay-title { font-size: 19px; font-weight: 700; margin-bottom: 14px; letter-spacing: 2px; }
    .pay-row { display: flex; justify-content: space-between; padding: 10px 0; font-size: 14px; color: #b9c6e0; }
    .pay-row .amount { color: #4da3ff; font-size: 22px; font-weight: 800; text-shadow: 0 0 18px rgba(77,163,255,0.45); }
    .pay-methods { display: flex; gap: 10px; margin: 14px 0; }
    .pay-method {
        flex: 1;
        border: 1px solid rgba(120, 170, 255, 0.16);
        border-radius: 12px;
        padding: 12px;
        cursor: pointer;
        transition: all .18s;
        background: rgba(255, 255, 255, 0.03);
    }
    .pay-method.active {
        border-color: #4da3ff;
        background: linear-gradient(135deg, rgba(77,163,255,0.22), rgba(140,90,255,0.16));
        box-shadow: 0 0 20px rgba(77, 163, 255, 0.25);
    }
    .pay-method .method-title { font-size: 15px; font-weight: 600; color: #e8eefc; }
    .pay-method.active .method-title { color: #8cc4ff; }
    .pay-method .method-desc { font-size: 12px; color: #7d8db0; margin-top: 4px; line-height: 1.4; }
    .cancel-btn { margin-top: 10px; color: #7d8db0 !important; border-color: rgba(120,170,255,0.15) !important; background: transparent !important; }

    /* ===== 成功页 ===== */
    .success-card { text-align: center; }
    .success-ring { display: flex; justify-content: center; margin: 18px 0 6px; }
    .checkmark { width: 72px; height: 72px; }
    .checkmark-circle { stroke: #35d18b; stroke-width: 2; stroke-dasharray: 166; stroke-dashoffset: 166; animation: stroke .5s cubic-bezier(.65,0,.45,1) forwards; }
    .checkmark-check { stroke: #35d18b; stroke-width: 4; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 48; stroke-dashoffset: 48; animation: stroke .35s .4s cubic-bezier(.65,0,.45,1) forwards; }
    @keyframes stroke { 100% { stroke-dashoffset: 0; } }
    .success-title { font-size: 22px; font-weight: 800; color: #e8fdf4; margin-top: 6px; text-shadow: 0 0 22px rgba(53,209,139,0.5); }
    .success-amount { font-size: 30px; font-weight: 800; color: #35d18b; margin: 10px 0 4px; }
    .success-sub { font-size: 13px; color: #8fa0c2; margin-bottom: 16px; }
    .success-tip { display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 14px; color: #c7d6f2; margin-bottom: 18px; }
    .tip-dot { width: 8px; height: 8px; border-radius: 50%; background: #4da3ff; box-shadow: 0 0 10px #4da3ff; animation: pulse 1s infinite; }
    @keyframes pulse { 50% { opacity: .35; } }
</style>
