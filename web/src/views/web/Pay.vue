<template>
    <div class="pay-page">
        <div class="pay-card">
            <div class="pay-title">订单支付</div>
            <div class="pay-row"><span>订单号</span><span>{{ orderNo || (returning ? '确认中…' : '') }}</span></div>
            <div class="pay-row"><span>应付金额</span><span class="amount">¥{{ amountText }}</span></div>

            <template v-if="type === 'alipay'">
                <div class="pay-tip">正在跳转支付宝安全收银台…</div>
                <van-button type="primary" block round @click="pay">重新打开收银台</van-button>
            </template>
            <template v-else-if="type === 'mock'">
                <div class="pay-tip">当前为模拟支付通道（未配置支付宝沙箱）</div>
                <van-button type="success" block round :loading="paying" @click="mockPay">模拟支付成功</van-button>
            </template>
            <template v-else>
                <van-button type="primary" block round :loading="paying" @click="pay">去支付</van-button>
            </template>
            <van-button plain block style="margin-top:10px;" @click="cancel">返回</van-button>
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
                type: '',        // mock | alipay
                redirectUrl: '',
                paying: false,
                returning: false // 正在确认支付宝跳回结果
            }
        },
        mounted() {
            const q = this.$route.query
            this.orderId = q.orderId
            this.spotId = q.spotId || ''
            // 从支付宝收银台跳回（return_url 携带 out_trade_no/sign 等参数）：主动验签+查单兜底确认
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
            async fetchOrder() {
                try {
                    const res = await getOrderDetail(this.orderId)
                    this.orderNo = res.data.orderNo
                    this.amountText = Number(res.data.totalAmount || 0).toFixed(2)
                } catch (e) {
                    showToast('获取订单信息失败')
                }
            },
            // 支付宝同步跳转兜底确认：后端验签 + alipay.trade.query 权威查单
            async confirmReturn(q) {
                this.paying = true
                try {
                    const res = await alipayReturn(q)
                    showToast('支付成功')
                    this.$router.replace({ path: '/orders', query: { refreshed: Date.now() } })
                } catch (e) {
                    // 验签失败/尚未支付成功/异步回调未到达：回到支付页，可重新发起或稍后查看订单
                    showToast(e.msg || e.message || '支付结果确认中，请稍后在订单列表查看')
                    this.returning = false
                    if (this.orderId) {
                        this.fetchOrder()
                    }
                } finally {
                    this.paying = false
                }
            },
            async pay() {
                this.paying = true
                try {
                    const res = await payOrder(this.orderId)
                    const d = res.data || {}
                    this.type = d.type || 'mock'
                    this.redirectUrl = d.redirectUrl || ''
                    if (this.type === 'alipay' && this.redirectUrl) {
                        window.location.href = this.redirectUrl
                        return
                    }
                    showToast('已生成支付单，请确认支付')
                } catch (e) {
                    showToast(e.msg || e.message || '支付失败，请重试')
                } finally {
                    this.paying = false
                }
            },
            async mockPay() {
                this.paying = true
                try {
                    await mockConfirmPay(this.orderId)
                    showToast('支付成功')
                    this.$router.replace({ path: '/orders', query: { refreshed: Date.now() } })
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
    .pay-tip { color: #969799; font-size: 12px; margin: 12px 0; text-align: center; }
</style>