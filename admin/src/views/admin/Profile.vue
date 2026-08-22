<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">个人中心</div>
                <div class="page-subtitle">维护管理员账号信息</div>
            </div>
        </div>
        <el-card style="max-width: 620px;">
            <template #header>个人信息</template>
            <el-form :model="form" label-width="80px">
                <el-form-item label="昵称">
                    <el-input v-model="form.nickname" style="width:300px;" />
                </el-form-item>
                <el-form-item label="手机号">
                    <el-input v-model="form.phone" style="width:300px;" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleSave">保存修改</el-button>
                    <el-button style="margin-left:12px;" @click="handleLogout">退出登录</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <el-card style="max-width: 620px; margin-top: 16px;">
            <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>商户沙箱账号</span>
                    <el-button type="warning" plain size="small" @click="handleReset">重置余额</el-button>
                </div>
            </template>
            <el-descriptions :column="1" border>
                <el-descriptions-item label="商户账号">{{ merchant.account || '-' }}</el-descriptions-item>
                <el-descriptions-item label="登录密码">{{ merchant.password || '111111' }}</el-descriptions-item>
                <el-descriptions-item label="商户账号 PID">{{ merchant.pidUid || '-' }}</el-descriptions-item>
                <el-descriptions-item label="账户余额">￥{{ fmt(merchant.balance) }}</el-descriptions-item>
            </el-descriptions>
            <div style="margin-top: 12px; font-size: 12px; color: #909399;">模拟支付/退款会同步更新商户与买家余额；对账后续由两端订单数据汇总。</div>
        </el-card>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile } from '@/api/user'
    import { getSandboxMerchant, resetSandbox } from '@/api/pay'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'Profile',
        data() {
            return {
                form: { nickname: '', phone: '' },
                merchant: {}
            }
        },
        mounted() {
            const info = useUserStore().userInfo
            this.form.nickname = info.nickname || ''
            this.form.phone = info.phone || ''
            this.loadMerchant()
        },
        methods: {
            fmt(v) {
                return Number(v || 0).toFixed(2)
            },
            async loadMerchant() {
                try {
                    const res = await getSandboxMerchant()
                    this.merchant = res.data || {}
                } catch (e) {
                    console.error('load merchant sandbox account failed', e)
                }
            },
            async handleReset() {
                try {
                    await ElMessageBox.confirm('确认将商户余额重置为 1000000.00 ？', '重置沙箱', { type: 'warning' })
                    await resetSandbox()
                    ElMessage.success('已重置')
                    this.loadMerchant()
                } catch (e) {
                    // canceled or failed
                }
            },
            handleLogout() {
                ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
                    .then(() => {
                        const store = useUserStore()
                        store.logout()
                        window.location.href = '/login'
                    })
                    .catch(() => {})
            },
            async handleSave() {
                try {
                    await updateProfile(this.form)
                    await useUserStore().getUserInfo()
                    ElMessage.success('修改成功')
                } catch (e) {
                    // 业务错误已由 request 拦截器统一提示，这里只记录日志，避免未捕获异常
                    console.error('保存失败:', e)
                }
            }
        }
    }
</script>