<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">沙箱对账</div>
                <div class="page-subtitle">支付宝沙箱账户镜像（本地模拟）：页面支付/退款会同步更新商户与买家余额</div>
            </div>
            <div class="page-header-right">
                <el-button type="warning" plain @click="handleReset">重置余额</el-button>
                <el-button type="primary" @click="loadAll">刷新</el-button>
            </div>
        </div>

        <el-row :gutter="16" class="account-cards">
            <el-col :span="12">
                <el-card shadow="hover">
                    <div class="acct-role">商户账号</div>
                    <div class="acct-balance">￥{{ fmt(merchant.balance) }}</div>
                    <div class="acct-meta">{{ merchant.account }}（PID {{ merchant.pidUid }}）</div>
                </el-card>
            </el-col>
            <el-col :span="12">
                <el-card shadow="hover">
                    <div class="acct-role">买家账号</div>
                    <div class="acct-balance">￥{{ fmt(buyer.balance) }}</div>
                    <div class="acct-meta">{{ buyer.account }}（UID {{ buyer.pidUid }}）</div>
                </el-card>
            </el-col>
        </el-row>

        <el-card class="flow-card">
            <template #header>余额变动流水</template>
            <el-table :data="flows" border stripe size="small" style="width: 100%">
                <el-table-column prop="orderNo" label="订单号" width="230" show-overflow-tooltip />
                <el-table-column label="类型" width="80">
                    <template #default="{ row }">
                        <el-tag :type="row.bizType === 'pay' ? 'success' : 'warning'" size="small">
                            {{ row.bizType === 'pay' ? '支付' : '退款' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="账户" width="80">
                    <template #default="{ row }">{{ row.role === 'merchant' ? '商户' : '买家' }}</template>
                </el-table-column>
                <el-table-column label="方向" width="80">
                    <template #default="{ row }">
                        <span :style="{ color: row.direction === 'in' ? '#67c23a' : '#f56c6c' }">
                            {{ row.direction === 'in' ? '收入' : '支出' }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="金额" width="120">
                    <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
                </el-table-column>
                <el-table-column label="变动后余额" width="140">
                    <template #default="{ row }">￥{{ fmt(row.balanceAfter) }}</template>
                </el-table-column>
                <el-table-column prop="createTime" label="时间" />
            </el-table>
        </el-card>
    </div>
</template>

<script>
    import { getSandboxAccounts, getSandboxFlows, resetSandbox } from '@/api/pay'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'SandboxAccount',
        data() {
            return {
                accounts: [],
                flows: []
            }
        },
        computed: {
            merchant() {
                return this.accounts.find(a => a.role === 'merchant') || { balance: 0, account: '', pidUid: '' }
            },
            buyer() {
                return this.accounts.find(a => a.role === 'buyer') || { balance: 0, account: '', pidUid: '' }
            }
        },
        mounted() {
            this.loadAll()
        },
        methods: {
            fmt(v) {
                return Number(v || 0).toFixed(2)
            },
            async loadAll() {
                try {
                    const [a, f] = await Promise.all([getSandboxAccounts(), getSandboxFlows()])
                    this.accounts = a.data || []
                    this.flows = f.data || []
                } catch (e) {
                    console.error('加载沙箱数据失败', e)
                }
            },
            async handleReset() {
                try {
                    await ElMessageBox.confirm('确认将商户/买家余额重置为 1000000.00 并清空流水？', '重置沙箱', { type: 'warning' })
                    await resetSandbox()
                    ElMessage.success('已重置')
                    this.loadAll()
                } catch (e) {
                    // 取消或失败
                }
            }
        }
    }
</script>

<style scoped>
    .admin-page { padding: 20px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-title { font-size: 20px; font-weight: 700; }
    .page-subtitle { color: #909399; font-size: 13px; margin-top: 4px; }
    .account-cards { margin-bottom: 16px; }
    .acct-role { font-size: 14px; color: #909399; }
    .acct-balance { font-size: 28px; font-weight: 700; margin: 8px 0; color: #303133; }
    .acct-meta { font-size: 12px; color: #c0c4cc; }
    .flow-card { margin-top: 4px; }
</style>
