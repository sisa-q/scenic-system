<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">订单管理</div>
                <div class="page-subtitle">检索与处理游客订单、退款与批量操作</div>
            </div>
            <div class="page-header-right" v-if="pendingRefundCount > 0">
                <el-tag type="danger" effect="dark" size="large">
                    待处理退款 {{ pendingRefundCount }}
                </el-tag>
            </div>
        </div>
        <!-- 搜索栏 -->
        <div class="toolbar">
            <el-input v-model="searchKey" placeholder="订单号搜索" style="width:220px;" clearable />
            <el-select v-model="statusFilter" placeholder="全部状态" style="width:130px;" clearable @change="fetchList">
                <el-option label="待支付" :value="0" />
                <el-option label="已支付" :value="1" />
                <el-option label="已使用" :value="2" />
                <el-option label="已退款" :value="3" />
                <el-option label="已失效" :value="4" />
                <el-option label="已申请待退款" :value="5" />
            </el-select>
            <el-button @click="fetchList">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
            <el-button @click="fetchList">刷新</el-button>
            <el-button
                    v-if="selectedIds.length > 0"
                    type="danger"
                    @click="handleBatchDelete"
            >
                删除选中 ({{ selectedIds.length }})
            </el-button>
        </div>

        <!-- 数据表格 -->
        <el-table
                :data="tableData"
                border
                style="width:100%"
                @selection-change="handleSelectionChange"
                :row-class-name="rowClassName"
                :cell-style="cellStyle"
                ref="tableRef"
        >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column prop="spotName" label="景点" />
            <el-table-column prop="policyName" label="票种" />
            <el-table-column prop="startTime" label="时段" width="180" />
            <el-table-column prop="quantity" label="数量" />
            <el-table-column prop="totalAmount" label="总金额" />
            <el-table-column prop="status" label="状态" width="180">
                <template #default="{ row }">
                    <el-tag :type="statusTagType(row)" size="small">
                        {{ statusLabel(row) }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="createTime" label="下单时间" width="180" />
            <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                    <el-button size="small" @click="showDetail(row)">详情</el-button>
                    <el-button v-if="row.status === 1 || row.status === 5" size="small" type="danger" @click="handleRefund(row.id)">退款</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 订单详情弹窗 -->
        <el-dialog v-model="detailVisible" title="订单详情" width="500px">
            <div v-if="currentOrder">
                <el-descriptions :column="1" border>
                    <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
                    <el-descriptions-item label="状态">{{ statusLabel(currentOrder) }}</el-descriptions-item>
                    <el-descriptions-item label="景点">{{ currentOrder.spotName }}</el-descriptions-item>
                    <el-descriptions-item label="票种">{{ currentOrder.policyName }}</el-descriptions-item>
                    <el-descriptions-item label="时段">{{ currentOrder.startTime }} - {{ currentOrder.endTime }}</el-descriptions-item>
                    <el-descriptions-item label="数量">{{ currentOrder.quantity }}</el-descriptions-item>
                    <el-descriptions-item label="单价">¥{{ currentOrder.policyPrice || 0 }}</el-descriptions-item>
                    <el-descriptions-item label="总金额">¥{{ currentOrder.totalAmount || 0 }}</el-descriptions-item>
                    <el-descriptions-item label="下单时间">{{ currentOrder.createTime }}</el-descriptions-item>
                    <el-descriptions-item label="支付时间">{{ currentOrder.payTime || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="退款时间">{{ currentOrder.refundTime || '-' }}</el-descriptions-item>
                </el-descriptions>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
                <el-button v-if="currentOrder && (currentOrder.status === 1 || currentOrder.status === 5)" type="danger" @click="handleRefund(currentOrder.id)">退款</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import { getOrderList, refundOrder, batchDeleteOrders } from '@/api/order'
    import { refreshPendingPayments } from '@/api/pay'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'OrderManage',
        data() {
            return {
                searchKey: '',
                statusFilter: undefined,
                tableData: [],
                detailVisible: false,
                currentOrder: null,
                selectedIds: [],
                refreshTimer: null,
                lastPendingRefresh: 0,
                statusMap: {
                    0: { label: '待支付', type: 'warning' },
                    1: { label: '已支付', type: 'success' },
                    2: { label: '已使用', type: 'info' },
                    3: { label: '已退款', type: 'danger' },
                    4: { label: '已失效', type: 'info' },
                    5: { label: '已申请待退款', type: 'danger' }
                }
            }
        },
        computed: {
            // 待处理退款计数：已申请待退款 + 已支付已停用待退款
            pendingRefundCount() {
                return this.tableData.filter(r => r.status === 5 || (r.status === 1 && r.disabled)).length
            }
        },
        async mounted() {
            await this.fetchList()
            // 列表页兜底：有待支付订单时主动查支付宝确认（通知未到也能自动变已支付）
            this.tryRefreshPending(true)
            this.startAutoRefresh()
            document.addEventListener('visibilitychange', this.onVisibilityChange)
        },
        beforeUnmount() {
            if (this.refreshTimer) {
                clearInterval(this.refreshTimer)
                this.refreshTimer = null
            }
            document.removeEventListener('visibilitychange', this.onVisibilityChange)
        },
        methods: {
            // 状态文本（含停用类型）
            statusLabel(row) {
                if (row.status === 0 && row.disabled) return '待支付已停用'
                if (row.status === 1 && row.disabled) return '已支付已停用待退款'
                return this.statusMap[row.status]?.label || '未知'
            },
            // 状态标签颜色
            statusTagType(row) {
                if (row.status === 1 && row.disabled) return 'danger'
                if (row.status === 0 && row.disabled) return 'warning'
                return this.statusMap[row.status]?.type || 'info'
            },
            // 是否待处理退款（已申请待退款 / 已支付已停用待退款）
            isRefundPending(row) {
                return row.status === 5 || (row.status === 1 && row.disabled)
            },
            // 红色行（类）
            rowClassName({ row }) {
                return this.isRefundPending(row) ? 'order-refund-red' : ''
            },
            // 红色单元格（行内样式，保证立即显现）
            cellStyle({ row }) {
                return this.isRefundPending(row) ? { background: '#fdecec !important' } : {}
            },
            // 静默刷新数据（不清除选中）
            async refreshData() {
                try {
                    const res = await getOrderList({ key: this.searchKey, status: this.statusFilter })
                    this.tableData = res.data || []
                } catch (e) {
                    console.error('获取订单失败:', e)
                }
            },
            async fetchList() {
                await this.refreshData()
                // 清除选中状态
                this.selectedIds = []
                this.$refs.tableRef?.clearSelection()
            },
            // 页面重新可见时立即刷新（从游客端切回管理端时红标立即出现）
            onVisibilityChange() {
                if (!document.hidden) {
                    this.refreshData()
                    this.tryRefreshPending()
                }
            },
            // 每 2 秒自动刷新：游客退款申请后红色标记几乎立即反映，直到转为已退款才取消
            // 列表页兜底：有待支付订单时主动查支付宝确认（静默）
            async tryRefreshPending(force = false) {
                const hasPending = (this.tableData || []).some(o => o.status === 0)
                if (!hasPending) return
                if (!force && Date.now() - this.lastPendingRefresh < 30000) return
                this.lastPendingRefresh = Date.now()
                try {
                    await refreshPendingPayments({ silent: true })
                    await this.refreshData()
                } catch (e) {
                    // 静默失败
                }
            },
            startAutoRefresh() {
                if (this.refreshTimer) clearInterval(this.refreshTimer)
                this.refreshTimer = setInterval(() => {
                    this.refreshData()
                    // 列表页兜底：每 30 秒自动查一次待支付订单（已支付则确认）
                    this.tryRefreshPending()
                }, 2000)
            },
            resetSearch() {
                this.searchKey = ''
                this.statusFilter = undefined
                this.fetchList()
            },
            showDetail(row) {
                this.currentOrder = row
                this.detailVisible = true
            },

            // ====== 表格多选 ======
            handleSelectionChange(selection) {
                this.selectedIds = selection.map(item => item.id)
            },

            // ====== 退款 ======
            handleRefund(id) {
                ElMessageBox.confirm('确认退款？', '提示', { type: 'warning' })
                    .then(async () => {
                        await refundOrder(id)
                        ElMessage.success('退款成功')
                        this.fetchList()
                        if (this.detailVisible) {
                            this.detailVisible = false
                        }
                    })
                    .catch(() => {})
            },

            // ====== 批量删除 ======
            async handleBatchDelete() {
                if (this.selectedIds.length === 0) {
                    ElMessage.warning('请选择要删除的订单')
                    return
                }
                try {
                    await ElMessageBox.confirm(
                        `确定要删除选中的 ${this.selectedIds.length} 条订单吗？此操作不可恢复！`,
                        '警告',
                        { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
                    )
                    await batchDeleteOrders(this.selectedIds)
                    ElMessage.success(`成功删除 ${this.selectedIds.length} 条订单`)
                    this.fetchList()
                } catch (e) {
                    // 用户取消
                    if (e !== 'cancel') {
                        ElMessage.error(e.msg || '删除失败')
                    }
                }
            }
        }
    }
</script>

<style scoped>
    /* 退款相关订单红色标记 */
    :deep(.el-table .order-refund-red) {
        background: #fdecec !important;
    }
    :deep(.el-table .order-refund-red:hover > td) {
        background: #fbdada !important;
    }
</style>

