<template>
    <div class="order-item" @click="$emit('click')">
        <div class="order-header">
            <span class="order-no">订单号：{{ orderNo }}</span>
            <span class="order-status" :style="{ color: statusColor }">{{ statusText }}</span>
        </div>
        <div class="order-body">
            <span class="order-name">{{ spotName }}</span>
            <span class="order-amount">¥{{ totalAmount }}</span>
        </div>
        <div class="order-footer">
            <span class="order-time">{{ createTime }}</span>
            <span class="order-qty">×{{ quantity }}</span>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'OrderItem',
        props: {
            orderNo: { type: String, default: '' },
            spotName: { type: String, default: '' },
            totalAmount: { type: Number, default: 0 },
            quantity: { type: Number, default: 1 },
            createTime: { type: String, default: '' },
            status: { type: Number, default: 0 },
            // 景点/时段是否已停用（后端填充）
            disabled: { type: Boolean, default: false }
        },
        emits: ['click'],
        computed: {
            statusText() {
                // 已停用状态（景点/时段停用）
                if (this.status === 0 && this.disabled) return '待支付已停用'
                if (this.status === 1 && this.disabled) return '已支付已停用待退款'
                if (this.status === 5) return '申请退款中'
                const map = ['待支付', '已支付', '已使用', '已退款', '已失效']
                return map[this.status] || '未知'
            },
            statusColor() {
                // 已停用待退款 / 申请退款中 红色标识
                if (this.status === 5) return '#ee0a24'
                if (this.status === 1 && this.disabled) return '#ee0a24'
                if (this.status === 0 && this.disabled) return '#faad14'
                const map = ['#faad14', '#52c41a', '#1890ff', '#ff4d4f', '#d9d9d9']
                return map[this.status] || '#333'
            }
        }
    }
</script>

<style scoped>
    .order-item {
        flex: 1;
        background: #fff;
        border-radius: 12px;
        padding: 14px 16px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
        cursor: pointer;
        min-width: 0; /* 防止flex溢出 */
    }
    .order-header {
        display: flex;
        justify-content: space-between;
        font-size: 13px;
        color: #969799;
        padding-bottom: 8px;
        border-bottom: 1px solid #f0f0f0;
        flex-wrap: wrap;
    }
    .order-no {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    .order-status {
        flex-shrink: 0;
        margin-left: 8px;
    }
    .order-body {
        display: flex;
        justify-content: space-between;
        padding: 12px 0 8px;
        font-size: 15px;
    }
    .order-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    .order-amount {
        font-weight: 700;
        color: #ee0a24;
        flex-shrink: 0;
        margin-left: 8px;
    }
    .order-footer {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
        color: #c8c9cc;
    }
    .order-time {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    .order-qty {
        flex-shrink: 0;
        margin-left: 8px;
    }
</style>