<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">?人中心</div>
                <div class="page-subtitle">维护账号信息与余额钱包</div>
            </div>
        </div>

        <el-card style="max-width: 720px;">
            <template #header>基本信息</template>
            <el-form :model="form" label-width="90px">
                <el-form-item label="头像">
                    <el-input v-model="form.avatar" placeholder="头像图片 URL" style="width:320px;" />
                </el-form-item>
                <el-form-item label="昵称">
                    <el-input v-model="form.nickname" style="width:320px;" />
                </el-form-item>
                <el-form-item label="?机号">
                    <el-input v-model="form.phone" style="width:320px;" />
                </el-form-item>
                <el-form-item label="邮箱">
                    <el-input v-model="form.email" placeholder="选填" style="width:320px;" />
                </el-form-item>
                <el-form-item label="性别">
                    <el-radio-group v-model="form.gender">
                        <el-radio value="男">男</el-radio>
                        <el-radio value="女">女</el-radio>
                        <el-radio value="保密">保密</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="生日">
                    <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择生日" style="width:320px;" />
                </el-form-item>
                <el-form-item label="个人简介">
                    <el-input v-model="form.signature" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="选填" style="width:320px;" />
                </el-form-item>

                <el-divider content-position="left">修改密码（不修改请留空）</el-divider>
                <el-form-item label="原密码">
                    <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" style="width:320px;" />
                </el-form-item>
                <el-form-item label="新密码">
                    <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" style="width:320px;" />
                </el-form-item>
                <el-form-item label="确认新密码">
                    <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" style="width:320px;" />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="handleSave">保存修改</el-button>
                    <el-button style="margin-left:12px;" @click="handleLogout">?出登录</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <el-card style="max-width: 720px; margin-top: 16px;">
            <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>我的余额</span>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <el-input-number v-model="amount" :min="0.01" :precision="2" :step="100" size="small" style="width: 160px;" />
                        <el-button type="primary" size="small" @click="handleWallet('recharge')">充值</el-button>
                        <el-button type="danger" plain size="small" @click="handleWallet('withdraw')">取现</el-button>
                    </div>
                </div>
            </template>
            <div style="font-size: 26px; font-weight: 700; color: #303133;">￥{{ fmt(userInfo.balance) }}</div>
            <div style="margin-top: 8px; font-size: 12px; color: #909399;">模拟支付从余额扣款，退款自动退回</div>
        </el-card>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile, walletRecharge, walletWithdraw } from '@/api/user'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'Profile',
        data() {
            return {
                form: {
                    avatar: '', nickname: '', phone: '', email: '', gender: '保密', birthday: '', signature: '',
                    oldPassword: '', newPassword: '', confirmPassword: ''
                },
                amount: 100
            }
        },
        computed: {
            userInfo() {
                return useUserStore().userInfo || {}
            }
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
        },
        methods: {
            fmt(v) {
                return Number(v || 0).toFixed(2)
            },
            isValidPhone(v) {
                return /^1[3-9]\d{9}$/.test(v)
            },
            isValidEmail(v) {
                return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(v)
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
                if (!this.form.nickname || !this.form.nickname.trim()) {
                    ElMessage.warning('请输入昵称')
                    return
                }
                if (!this.form.phone || !this.isValidPhone(this.form.phone)) {
                    ElMessage.warning('请输入正确的手机号')
                    return
                }
                if (this.form.email && !this.isValidEmail(this.form.email)) {
                    ElMessage.warning('请输入正确的邮箱')
                    return
                }
                if (this.form.newPassword || this.form.confirmPassword || this.form.oldPassword) {
                    if (this.form.newPassword.length < 6) {
                        ElMessage.warning('新密码至少 6 位')
                        return
                    }
                    if (this.form.newPassword !== this.form.confirmPassword) {
                        ElMessage.warning('两次输入的密码不一致')
                        return
                    }
                    if (!this.form.oldPassword) {
                        ElMessage.warning('请填写原密码')
                        return
                    }
                }
                const payload = {
                    nickname: this.form.nickname.trim(),
                    phone: this.form.phone.trim(),
                    avatar: this.form.avatar,
                    email: this.form.email,
                    gender: this.form.gender,
                    birthday: this.form.birthday,
                    signature: this.form.signature
                }
                if (this.form.newPassword) {
                    payload.oldPassword = this.form.oldPassword
                    payload.newPassword = this.form.newPassword
                }
                try {
                    await updateProfile(payload)
                    await useUserStore().getUserInfo()
                    ElMessage.success('修改成功')
                    this.form.oldPassword = ''
                    this.form.newPassword = ''
                    this.form.confirmPassword = ''
                } catch (e) {
                    console.error('save profile failed', e)
                }
            },
            async handleWallet(action) {
                const amt = Number(this.amount)
                if (!amt || amt <= 0) {
                    ElMessage.warning('请输入有效金额')
                    return
                }
                try {
                    const fn = action === 'recharge' ? walletRecharge : walletWithdraw
                    await fn({ amount: amt })
                    await useUserStore().getUserInfo()
                    ElMessage.success(action === 'recharge' ? '充值成功' : '取现成功')
                } catch (e) {
                    // error toast handled by interceptor
                }
            }
        }
    }
</script>
