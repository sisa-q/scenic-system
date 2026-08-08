<template>
    <div class="pay-page">
        <van-cell-group inset>
            <van-cell title="订单号" :value="orderNo" />
            <van-cell
                    title="支付金额"
                    :value="'¥' + amount"
                    style="font-size:20px;font-weight:700;color:#ee0a24;"
            />
        </van-cell-group>

        <div style="padding: 30px 16px;">
            <van-button type="danger" block round size="large" @click="pay" :loading="paying">
                {{ paying ? '支付中...' : '立即支付' }}
            </van-button>
            <van-button plain block round style="margin-top:12px;" @click="cancel">
                取消支付
            </van-button>
        </div>
    </div>
</template>

<script>
    import { payOrder, getOrderDetail } from '@/api/order'
    import { showToast } from 'vant'

    export default {
        name: 'Pay',
        data() {
            return {
                orderId: '',
                orderNo: '',
                amount: 0,
                paying: false
            }
        },
        mounted() {
            this.orderId = this.$route.query.orderId
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
                    this.amount = res.data.totalAmount
                } catch (e) {
                    showToast('获取订单信息失败')
                }
            },
            async pay() {
                this.paying = true
                try {
                    await payOrder(this.orderId)
                    showToast('支付成功')
                    // ✅ 跳转到订单列表，并强制刷新
                    this.$router.replace({
                        path: '/orders',
                        query: { refreshed: Date.now() }
                    })
                } catch (e) {
                    console.error('支付失败:', e)
                    showToast(e.msg || e.message || '支付失败，请重试')
                } finally {
                    this.paying = false
                }
            },
            cancel() {
                this.$router.back()
            }
        }
    }
</script>