<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">个人中心</div>
                <div class="page-subtitle">维护账号信息与余额钱包</div>
            </div>
        </div>
        <div class="profile-grid">
            <el-card class="profile-main">
                <template #header>基本信息</template>
                <el-form :model="form" label-width="90px">
                    <el-form-item label="头像"><el-input v-model="form.avatar" placeholder="头像图片 URL" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="昵称"><el-input v-model="form.nickname" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="手机号"><el-input v-model="form.phone" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="选填" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="性别">
                        <el-radio-group v-model="form.gender">
                            <el-radio value="男">男</el-radio>
                            <el-radio value="女">女</el-radio>
                            <el-radio value="保密">保密</el-radio>
                        </el-radio-group>
                    </el-form-item>
                    <el-form-item label="生日"><el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择生日" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="个人简介"><el-input v-model="form.signature" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="选填" style="max-width:420px;" /></el-form-item>
                    <el-divider content-position="left">修改密码（不修改请留空）</el-divider>
                    <el-form-item label="原密码"><el-input v-model="form.oldPassword" type="password" show-password placeholder="请填写原密码" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password placeholder="新密码至少 6 位" style="max-width:420px;" /></el-form-item>
                    <el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password placeholder="确认新密码" style="max-width:420px;" /></el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="handleSave">保存修改</el-button>
                        <el-button style="margin-left:12px;" @click="handleLogout">退出登录</el-button>
                    </el-form-item>
                </el-form>
            </el-card>
            <el-card class="profile-wallet">
                <template #header>我的余额</template>
                <div class="wallet-label">当前余额</div>
                <div class="wallet-balance">￥{{ fmt(userInfo.balance) }}</div>
                <div class="wallet-tip">模拟支付从余额扣款，退款自动退回</div>
            </el-card>
        </div>

        <el-card class="sandbox-card">
            <template #header>支付宝沙箱账号（公共测试）</template>
            <div class="sandbox-grid">
                <div class="sandbox-col">
                    <div class="sandbox-col-title">商户账号</div>
                    <div class="sandbox-row"><span>账号：</span><span>{{ sandbox.merchant.account || '-' }}</span></div>
                    <div class="sandbox-row"><span>登录密码：</span><span>{{ sandbox.merchant.password || '-' }}</span></div>
                    <div class="sandbox-row"><span>商户 PID：</span><span>{{ sandbox.merchant.pidUid || '-' }}</span></div>
                    <div class="sandbox-row"><span>账户余额：</span><span class="sb-balance">￥{{ fmt(sandbox.merchant.balance) }}</span></div>
                </div>
                <div class="sandbox-col">
                    <div class="sandbox-col-title">买家账号</div>
                    <div class="sandbox-row"><span>账号：</span><span>{{ sandbox.buyer.account || '-' }}</span></div>
                    <div class="sandbox-row"><span>登录密码：</span><span>{{ sandbox.buyer.password || '-' }}</span></div>
                    <div class="sandbox-row"><span>支付密码：</span><span>{{ sandbox.buyer.payPassword || '-' }}</span></div>
                    <div class="sandbox-row"><span>买家 UID：</span><span>{{ sandbox.buyer.pidUid || '-' }}</span></div>
                    <div class="sandbox-row"><span>账户余额：</span><span class="sb-balance">￥{{ fmt(sandbox.buyer.balance) }}</span></div>
                </div>
            </div>
            <div class="sandbox-tip">支付宝沙箱公共测试账号：所有游客共用，仅供演示，余额随支付/退款/充值联动。</div>
        </el-card>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile } from '@/api/user'
    import { getSandboxAccounts } from '@/api/pay'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'Profile',
        data() {
            return {
                form: { avatar: '', nickname: '', phone: '', email: '', gender: '保密', birthday: '', signature: '', oldPassword: '', newPassword: '', confirmPassword: '' },
                sandbox: { merchant: {}, buyer: {} }
            }
        },
        computed: {
            userInfo() { return useUserStore().userInfo || {} }
        },
        mounted() {
            const info = useUserStore().userInfo
            this.form.avatar = info.avatar || ''
            this.form.nickname = info.nickname || ''
            this.form.phone = info.phone || ''
            this.form.email = info.email || ''
            this.form.gender = info.gender || '保密'
            this.form.birthday = info.birthday || ''
            this.form.signature = info.signature || ''
            this.loadSandbox()
        },
        methods: {
            fmt(v) { return Number(v || 0).toFixed(2) },
            isValidPhone(v) { return /^1[3-9]\d{9}$/.test(v) },
            isValidEmail(v) { return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(v) },
            async loadSandbox() {
                try {
                    const res = await getSandboxAccounts()
                    this.sandbox = res.data || { merchant: {}, buyer: {} }
                } catch (e) {}
            },
            handleLogout() {
                ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
                    .then(async () => { const store = useUserStore(); await store.logout(); window.location.href = '/login' })
                    .catch(() => {})
            },
            async handleSave() {
                if (!this.form.nickname || !this.form.nickname.trim()) { ElMessage.warning('请输入昵称'); return }
                if (!this.form.phone || !this.isValidPhone(this.form.phone)) { ElMessage.warning('请输入正确的手机号'); return }
                if (this.form.email && !this.isValidEmail(this.form.email)) { ElMessage.warning('请输入正确的邮箱'); return }
                if (this.form.newPassword || this.form.confirmPassword || this.form.oldPassword) {
                    if (this.form.newPassword.length < 6) { ElMessage.warning('新密码至少 6 位'); return }
                    if (this.form.newPassword !== this.form.confirmPassword) { ElMessage.warning('两次输入的密码不一致'); return }
                    if (!this.form.oldPassword) { ElMessage.warning('请填写原密码'); return }
                }
                const payload = { nickname: this.form.nickname.trim(), phone: this.form.phone.trim(), avatar: this.form.avatar, email: this.form.email, gender: this.form.gender, birthday: this.form.birthday, signature: this.form.signature }
                if (this.form.newPassword) { payload.oldPassword = this.form.oldPassword; payload.newPassword = this.form.newPassword }
                try {
                    await updateProfile(payload)
                    await useUserStore().getUserInfo()
                    ElMessage.success('修改成功')
                    this.form.oldPassword = ''
                    this.form.newPassword = ''
                    this.form.confirmPassword = ''
                } catch (e) { console.error('save profile failed', e) }
            },
        }
    }
</script>

<style scoped>
    .profile-grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
    .profile-main { width: 100%; }
    .profile-main :deep(.el-form-item__label) { color: #b8c6e0; }
    .profile-wallet { position: sticky; top: 16px; }
    .wallet-label { font-size: 13px; color: #8fa0c2; }
    .wallet-balance { font-size: 30px; font-weight: 800; color: #4da3ff; margin: 8px 0; text-shadow: 0 0 20px rgba(77,163,255,0.35); }
    .wallet-tip { font-size: 12px; color: #5f7399; margin-bottom: 14px; }
    .wallet-ops { display: flex; flex-direction: column; gap: 10px; }
    .wallet-btns { display: flex; gap: 10px; }
    @media (max-width: 1100px) {
        .profile-grid { grid-template-columns: 1fr; }
        .profile-wallet { position: static; }
    }
    .sandbox-card { margin-top: 16px; }
    .sandbox-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    .sandbox-col-title { font-size: 15px; font-weight: 700; color: #e8eefc; margin-bottom: 10px; }
    .sandbox-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; color: #b8c6e0; border-bottom: 1px solid rgba(120,170,255,0.08); }
    .sb-balance { color: #4da3ff; font-weight: 700; }
    .sandbox-tip { margin-top: 12px; font-size: 12px; color: #5f7399; }
    @media (max-width: 768px) { .sandbox-grid { grid-template-columns: 1fr; } }
</style>
