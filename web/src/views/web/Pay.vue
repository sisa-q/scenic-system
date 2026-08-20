<template>
    <div class="pay-page">
        <div class="pay-card">
            <div class="pay-title">订单支付</div>
            <div class="pay-row"><span>订单号</span><span>{{ orderNo || (returning ? '确认中…' : '') }}</span></div>
            <div class="pay-row"><span>应付金额</span><span class="amount">￥{{ amountText }}</span></div>

            <!-- 支付方式选择：模拟支付 / 支付宝沙箱支付（两个模式独立） -->
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
            <van-button plain block style="margin-top:10px;" @click="cancel">取消</van-button>
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
                returning: false // 正在确认支付宝支付结果并跳转
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
        methods: {
            selectMode(m) {
                this.mode = m
            },
            async fetchOrder() {
                try {
                    const res = await getOrderDetail(this.orderId)
                    this.orderNo = res.data.orderNo
                    this.amountText = Number(res.data.totalAmount || 0).toFixed(2)
                } catch (e) {
                    showToast('获取订单信息失败')
                }
            },
            // 支付宝同步跳转兜底确认（当前未配置 return_url 时不会触发）
            async confirmReturn(q) {
                this.paying = true
                try {
                    const res = await alipayReturn(q)
                    showToast('支付成功')
                    this.$router.replace({ path: '/orders', query: { refreshed: Date.now(), status: 1 } })
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
                    this.$router.replace({ path: '/orders', query: { refreshed: Date.now(), status: 1 } })
                } catch (e) {
                    showToast(e.msg || e.message || '确认失败')
                } finally {
                    this.paying = false
                }
            },
            cancel() {
                if (this.spotId) {
                    this.$router.replace('/spot/' + this.spotId)
                } else {
                    this.$router.back()
                }
            }
        }
    }
</script>

<style scoped>
    .pay-page { min-height: 100vh; background: #f7f8fa; padding: 16px; box-sizing: border-box; }
    .pay-card { background: #fff; border-radius: 12px; padding: 20px 16px; }
    .pay-title { font-size: 18px; font-weight: 700; margin-bottom: 14px; }
    .pay-row { display: flex; justify-content: space-between; padding: 10px 0; color: #323233; font-size: 14px; }
    .pay-row .amount { color: #ee0a24; font-size: 20px; font-weight: 700; }
    .pay-methods { display: flex; gap: 10px; margin: 14px 0; }
    .pay-method {
        flex: 1;
        border: 1px solid #e8e8e8;
        border-radius: 10px;
        padding: 12px;
        cursor: pointer;
        transition: all .15s;
    }
    .pay-method.active {
        border-color: #1989fa;
        background: #ecf5ff;
    }
    .pay-method .method-title { font-size: 15px; font-weight: 600; color: #323233; }
    .pay-method.active .method-title { color: #1989fa; }
    .pay-method .method-desc { font-size: 12px; color: #969799; margin-top: 4px; line-height: 1.4; }
</style>
