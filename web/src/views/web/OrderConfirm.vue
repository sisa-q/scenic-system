<template>
    <div class="order-confirm page-container" v-if="slotInfo">
        <van-cell-group inset>
            <van-cell title="景点" :value="spotName" />
            <van-cell title="票种" :value="policyName" />
            <van-cell title="时段" :value="slotTime" />
            <van-cell title="单价" :value="'\u00a5' + price" />
            <van-cell title="余票" :value="remaining + ' 张'" />
            <van-cell title="数量">
                <template #right-icon>
                    <van-stepper v-model="quantity" min="1" :max="Math.max(1, remaining)" />
                </template>
            </van-cell>
            <van-cell title="合计" :value="'\u00a5' + totalPrice" style="font-weight:700;color:#ee0a24;" />
        </van-cell-group>

        <div class="tips">
            <p>实名制入园，请确保信息准确；入园时凭订单号核销。</p>
        </div>

        <div style="padding: 16px;">
            <van-button plain block style="margin-bottom:10px;" @click="goBack">返回</van-button>
            <van-button type="danger" block round @click="submitOrder">提交订单</van-button>
        </div>
    </div>
    <van-loading v-else style="margin-top: 40px;" />
</template>

<script>
    import { createOrder } from '@/api/order'
    import { getSlotById } from '@/api/ticket'
    import { showToast } from 'vant'

    export default {
        name: 'OrderConfirm',
        data() {
            return {
                slotId: '',
                spotId: '',
                spotName: '',
                policyName: '',
                price: 0,
                quantity: 1,
                slotInfo: null,
                remaining: 0,
                slotTime: ''
            }
        },
        computed: {
            totalPrice() {
                return (this.price * this.quantity).toFixed(2)
            }
        },
        mounted() {
            this.slotId = this.$route.query.slotId
            const qq = parseInt(this.$route.query.quantity, 10)
            if (qq > 0 && !isNaN(qq)) this.quantity = qq
            this.spotId = this.$route.query.spotId || ''
            if (!this.slotId) {
                showToast('参数错误')
                this.$router.back()
                return
            }
            // 以服务器的时段信息为准，防止前端传参不准
            this.fetchSlotInfo()
        },
        methods: {
            async fetchSlotInfo() {
                try {
                    const res = await getSlotById(this.slotId)
                    this.slotInfo = res.data
                    if (!this.slotInfo) {
                        showToast('时段不存在')
                        this.$router.back()
                        return
                    }
                    this.remaining = Math.max(0, (this.slotInfo.quota || 0) - (this.slotInfo.booked || 0))
                    if (this.quantity > this.remaining && this.remaining > 0) this.quantity = this.remaining
                    this.price = this.slotInfo.price || 0
                    this.policyName = this.slotInfo.policyName || this.$route.query.policyName || '故宫门票'
                    this.spotName = this.$route.query.spotName || this.slotInfo.spotName || '景区'
                    const start = this.slotInfo.startTime ? this.slotInfo.startTime.slice(0, 16) : ''
                    const end = this.slotInfo.endTime ? this.slotInfo.endTime.slice(0, 16) : ''
                    this.slotTime = start + ' - ' + end
                    if (this.slotInfo.status !== 1) {
                        showToast('该时段已关闭')
                        this.$router.back()
                        return
                    }
                    if (this.remaining <= 0) {
                        showToast('该时段已售罄')
                    }
                } catch (e) {
                    console.error('获取时段信息失败', e)
                    showToast('获取时段信息失败')
                }
            },
            goBack() {
                if (this.spotId) {
                    this.$router.replace('/spot/' + this.spotId)
                } else {
                    this.$router.back()
                }
            },
            async submitOrder() {
                if (this.remaining <= 0) {
                    showToast('该时段已售罄')
                    return
                }
                if (this.quantity > this.remaining) {
                    showToast('余票不足，请减少数量')
                    return
                }
                try {
                    const res = await createOrder({
                        slotId: this.slotId,
                        quantity: this.quantity
                    })
                    showToast('下单成功')
                    this.$router.push({ path: '/pay', query: { orderId: res.data.id, spotId: this.spotId } })
                } catch (e) {
                    showToast(e.msg || '下单失败')
                }
            }
        }
    }
</script>

<style scoped>
    .order-confirm {
        padding: 16px;
        background: transparent;
        min-height: 100vh;
    }
    .tips {
        margin: 12px 4px 0;
        color: #969799;
        font-size: 12px;
        line-height: 1.6;
    }
</style>
