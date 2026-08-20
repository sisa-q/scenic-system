<template>
    <div class="order-list">
        <!-- ========== 顶部标签栏 ========== -->
        <div class="order-header">
            <van-tabs v-model:active="activeStatus" @change="onTabChange">
                <van-tab title="全部" :name="-1" />
                <van-tab title="待支付" :name="0" />
                <van-tab title="已支付" :name="1" />
                <van-tab title="已使用" :name="2" />
                <van-tab title="已退款" :name="3" />
                <van-tab title="已失效" :name="4" />
            </van-tabs>
            <van-button
                    v-if="selectedIds.length > 0"
                    type="danger"
                    size="small"
                    plain
                    @click="handleHideSelected"
                    class="delete-btn"
            >
                隐藏选中 ({{ selectedIds.length }})
            </van-button>
        </div>

        <!-- ========== 批量隐藏工具栏 ========== -->
        <div v-if="showBatchToolbar" class="batch-toolbar">
            <span class="toolbar-info">
                可隐藏订单：<strong>{{ deletableCount }}</strong> 条
            </span>
            <van-button type="danger" size="small" round @click="handleHideAll">
                一键隐藏全部
            </van-button>
        </div>

        <!-- ========== 订单列表 ========== -->
        <div class="list" v-if="list.length">
            <div
                    v-for="item in list"
                    :key="item.id"
                    class="order-wrapper"
                    :class="{ 'order-selected': selectedIds.includes(item.id) }"
            >
                <input
                        v-if="item.status === 0 || item.status === 3"
                        type="checkbox"
                        :checked="selectedIds.includes(item.id)"
                        @change="toggleSelect(item.id)"
                        class="order-checkbox-input"
                />
                <OrderItem
                        v-bind="item"
                        @click="goDetail(item.id)"
                        :style="{ flex: 1 }"
                />
            </div>
        </div>
        <van-empty v-else description="暂无订单" />

        <TabBar />
    </div>
</template>

<script>
    import { getOrderList, hideOrders } from '@/api/order'
    import OrderItem from '@/components/web/OrderItem.vue'
    import TabBar from '@/components/web/TabBar.vue'
    import { showToast, showConfirmDialog } from 'vant'

    export default {
        name: 'OrderList',
        components: { OrderItem, TabBar },
        data() {
            return {
                activeStatus: -1,
                list: [],
                selectedIds: [],
                refreshTimer: null
            }
        },
        computed: {
            deletableCount() {
                return this.list.filter(item => item.status === 0 || item.status === 3).length
            },
            showBatchToolbar() {
                return this.deletableCount > 0
            }
        },
        // ✅ 每次进入页面（keep-alive 场景）重新加载
        activated() {
            this.fetchOrders()
        },
        mounted() {
            // 支付成功后带 status=1 跳转，自动切到“已支付”页签
            this.applyStatusQuery()
            this.fetchOrders()
            // 实时同步：每 3 秒静默刷新（支付/退款等状态变更即时可见）
            this.startAutoRefresh()
            document.addEventListener('visibilitychange', this.onVisibilityChange)
        },
        // 页面卸载时停止轮询，避免资源泄漏
        beforeUnmount() {
            this.stopAutoRefresh()
            document.removeEventListener('visibilitychange', this.onVisibilityChange)
        },
        // ✅ 监听路由参数变化，支付成功后刷新
        watch: {
            '$route.query.refreshed'() {
                this.fetchOrders()
            },
            // 支付成功后带 status=1 跳转，自动切到“已支付”页签
            '$route.query.status'() {
                this.applyStatusQuery()
                this.fetchOrders()
            }
        },
        methods: {
            async fetchOrders(silent = false) {
                const status = this.activeStatus === -1 ? undefined : this.activeStatus
                try {
                    const res = await getOrderList({ status }, { silent })
                    this.list = (res.data || []).slice().sort((a, b) => (b.id || 0) - (a.id || 0))
                    // 静默轮询不清空勾选，避免影响批量隐藏操作
                    if (!silent) {
                        this.selectedIds = []
                    }
                } catch (e) {
                    console.error('获取订单列表失败:', e)
                    if (!silent) showToast('获取订单列表失败')
                }
            },
            // ====== 实时同步：自动轮询 ======
            // 读取路由 status 参数并切换页签（支付成功 -> 已支付）
            applyStatusQuery() {
                const raw = this.$route.query.status
                if (raw === undefined || raw === null || raw === '') return
                const n = Number(raw)
                if (!Number.isNaN(n) && n >= -1 && n <= 5) {
                    this.activeStatus = n
                }
            },
            startAutoRefresh() {
                this.stopAutoRefresh()
                this.refreshTimer = setInterval(() => {
                    this.fetchOrders(true)
                }, 3000)
            },
            stopAutoRefresh() {
                if (this.refreshTimer) {
                    clearInterval(this.refreshTimer)
                    this.refreshTimer = null
                }
            },
            onVisibilityChange() {
                // 切回页面时立即刷新一次
                if (!document.hidden) {
                    this.fetchOrders(true)
                }
            },
            onTabChange() {
                this.fetchOrders()
            },
            goDetail(id) {
                this.$router.push(`/order/${id}`)
            },
            toggleSelect(id) {
                const index = this.selectedIds.indexOf(id)
                if (index > -1) {
                    this.selectedIds.splice(index, 1)
                } else {
                    this.selectedIds.push(id)
                }
            },

            // ====== 隐藏已选中的订单 ======
            async handleHideSelected() {
                if (this.selectedIds.length === 0) {
                    showToast('请选择要隐藏的订单')
                    return
                }
                try {
                    await showConfirmDialog({
                        title: '确认隐藏',
                        message: `确定要隐藏选中的 ${this.selectedIds.length} 条订单吗？隐藏后您将不再看到它们，但订单数据仍保留在系统中。`,
                        confirmButtonColor: '#ee0a24'
                    })
                    await hideOrders(this.selectedIds)
                    showToast(`已隐藏 ${this.selectedIds.length} 条订单`)
                    await this.fetchOrders()
                } catch (e) {
                    if (e.msg) showToast(e.msg)
                }
            },

            // ====== 一键隐藏所有可隐藏的订单 ======
            async handleHideAll() {
                const deletableIds = this.list
                    .filter(item => item.status === 0 || item.status === 3)
                    .map(item => item.id)

                if (deletableIds.length === 0) {
                    showToast('没有可隐藏的订单')
                    return
                }

                try {
                    await showConfirmDialog({
                        title: '确认隐藏全部',
                        message: `确定要隐藏所有可隐藏的订单吗？（共 ${deletableIds.length} 条）隐藏后您将不再看到它们，但订单数据仍保留在系统中。`,
                        confirmButtonColor: '#ee0a24'
                    })
                    await hideOrders(deletableIds)
                    showToast(`已隐藏 ${deletableIds.length} 条订单`)
                    await this.fetchOrders()
                } catch (e) {
                    if (e.msg) showToast(e.msg)
                }
            }
        }
    }
</script>

<style scoped>
    .order-list {
        padding-bottom: 60px;
        min-height: 100vh;
        background: transparent;
    }

    /* 订单页固定导航栏：吸顶 + 深色背景全覆盖 */
    .order-header {
        position: sticky;
        top: 0;
        z-index: 100;
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: rgba(10, 16, 34, 0.94);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        padding-right: 12px;
        border-bottom: 1px solid rgba(120, 170, 255, 0.16);
        min-height: 50px;
    }
    .order-header .van-tabs {
        flex: 1;
    }
    /* 只在本页作用域内让状态 tab 深色化 */
    .order-header :deep(.van-tabs__wrap) { background: transparent !important; }
    .order-header :deep(.van-tab) { color: #8fa0c2 !important; }
    .order-header :deep(.van-tab--active) { color: #4da3ff !important; }
    .order-header :deep(.van-tabs__line) { background: #4da3ff !important; }
    .delete-btn {
        flex-shrink: 0;
        margin-left: 8px;
    }
    .order-header .van-tabs {
        flex: 1;
    }
    .delete-btn {
        flex-shrink: 0;
        margin-left: 8px;
    }

    .batch-toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 16px;
        background: rgba(16, 28, 56, 0.85);
        border-bottom: 1px solid rgba(120, 170, 255, 0.14);
        margin: 0 0 4px 0;
    }
    .toolbar-info {
        font-size: 14px;
        color: #aebcd8;
    }
    .toolbar-info strong {
        color: #4da3ff;
        font-size: 16px;
    }
    .toolbar-info strong {
        color: #1989fa;
        font-size: 16px;
    }

    .list {
        padding: 12px 16px;
    }
    .order-wrapper {
        display: flex;
        align-items: stretch;
        gap: 10px;
        background: #fff;
        border-radius: 12px;
        margin-bottom: 12px;
        padding: 6px 10px 6px 6px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
        transition: all 0.2s;
    }
    .order-selected {
        border: 2px solid #1989fa;
        background: #f0f7ff;
    }
    .order-checkbox-input {
        width: 20px;
        height: 20px;
        flex-shrink: 0;
        margin: 0;
        cursor: pointer;
        align-self: center;
        accent-color: #1989fa;
    }
</style>
