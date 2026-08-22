<template>
    <div class="user-center">
        <div class="uc-header">
            <div class="uc-header-title">个人中心</div>
            <van-icon name="setting-o" class="uc-header-action" @click="goProfile" />
        </div>
        <div class="uc-body">
            <div class="uc-card uc-account" @click="goProfile">
                <van-image round width="60" height="60" :src="userInfo.avatar || 'https://img.yzcdn.cn/vant/cat.jpeg'" fit="cover" class="uc-avatar" />
                <div class="uc-info">
                    <div class="uc-name">{{ userInfo.nickname || '未设置' }}</div>
                    <div class="uc-meta">{{ userInfo.phone || '未绑定手机号' }} ? {{ userInfo.role === 'admin' ? '管理员' : '普通游客' }}</div>
                    <div class="uc-meta">邮箱：{{ userInfo.email || '未设置' }} ? 性别：{{ userInfo.gender || '保密' }}</div>
                </div>
                <van-icon name="arrow" class="uc-arrow" />
            </div>
            <div class="uc-section-title">我的余额</div>
            <div class="uc-card uc-wallet">
                <div class="uc-wallet-top">
                    <div class="uc-wallet-label">当前余额</div>
                    <div class="uc-wallet-balance">￥{{ fmt(userInfo.balance) }}</div>
                </div>
                <div class="uc-wallet-tip">模拟支付从余额扣款，退款自动退回</div>
                <div class="uc-wallet-ops">
                    <van-field v-model="amount" type="number" label="金额" placeholder="请输入金额" class="uc-amount" />
                    <div class="uc-wallet-btns">
                        <van-button type="primary" block round @click="handleWallet('recharge')">充值</van-button>
                        <van-button type="danger" block round plain @click="handleWallet('withdraw')">取现</van-button>
                    </div>
                </div>
            </div>
            <div class="uc-actions">
                <van-button type="default" block round @click="handleLogout">退出登录</van-button>
                <van-button type="danger" block round plain @click="handleDeleteAccount">注销账号</van-button>
            </div>
        </div>
        <TabBar />
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { deleteAccount, walletRecharge, walletWithdraw } from '@/api/user'
    import TabBar from '@/components/web/TabBar.vue'
    import { showConfirmDialog, showToast } from 'vant'

    export default {
        name: 'UserCenter',
        components: { TabBar },
        data() {
            return { refreshTimer: null, amount: 100 }
        },
        computed: {
            userInfo() { return useUserStore().userInfo || {} }
        },
        methods: {
            fmt(v) { return Number(v || 0).toFixed(2) },
            goProfile() {
                const token = localStorage.getItem('token')
                if (!token) { showToast('请先登录'); this.$router.replace('/login'); return }
                this.$router.push('/profile')
            },
            async handleWallet(action) {
                const amt = Number(this.amount)
                if (!amt || amt <= 0) { showToast('请输入有效金额'); return }
                try {
                    const fn = action === 'recharge' ? walletRecharge : walletWithdraw
                    await fn({ amount: amt })
                    await useUserStore().getUserInfo()
                    showToast(action === 'recharge' ? '充值成功' : '取现成功')
                } catch (e) {}
            },
            startAutoRefresh() {
                this.stopAutoRefresh()
                this.refreshTimer = setInterval(async () => { try { await useUserStore().getUserInfo({ silent: true }) } catch (e) {} }, 5000)
            },
            stopAutoRefresh() { if (this.refreshTimer) { clearInterval(this.refreshTimer); this.refreshTimer = null } },
            onVisibilityChange() { if (!document.hidden) { useUserStore().getUserInfo({ silent: true }).catch(() => {}) } },
            async handleLogout() {
                try {
                    await showConfirmDialog({ title: '提示', message: '确定退出登录吗？' })
                    const store = useUserStore()
                    await store.logout()
                    showToast('已退出登录')
                    this.$router.replace('/login')
                } catch (e) {}
            },
            async handleDeleteAccount() {
                try {
                    await showConfirmDialog({ title: '危险操作', message: '确认注销账号？此操作不可恢复！', confirmButtonText: '确认注销', confirmButtonColor: '#ee0a24' })
                    await showConfirmDialog({ title: '再次确认', message: '真的要注销账号吗？', confirmButtonText: '确定注销', confirmButtonColor: '#ee0a24' })
                    await deleteAccount()
                    const store = useUserStore()
                    store.logout()
                    showToast('账号已注销')
                    this.$router.replace('/login')
                } catch (e) {}
            }
        },
        mounted() {
            const token = localStorage.getItem('token')
            if (!token) { this.$router.replace('/login'); return }
            this.startAutoRefresh()
            document.addEventListener('visibilitychange', this.onVisibilityChange)
        },
        beforeUnmount() {
            this.stopAutoRefresh()
            document.removeEventListener('visibilitychange', this.onVisibilityChange)
        }
    }
</script>

<style scoped>
    .user-center { min-height: 100vh; padding-bottom: 70px; background: radial-gradient(900px 420px at 15% -5%, rgba(52,130,255,0.14), transparent 60%), radial-gradient(800px 400px at 100% 8%, rgba(150,96,255,0.10), transparent 55%), linear-gradient(160deg, #070b18 0%, #0c1730 55%, #0a1226 100%); }
    .uc-header { position: sticky; top: 0; z-index: 100; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; height: 50px; background: rgba(10,16,34,0.94); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); border-bottom: 1px solid rgba(120,170,255,0.16); }
    .uc-header-title { font-size: 17px; font-weight: 700; color: #e8eefc; letter-spacing: 2px; }
    .uc-header-action { font-size: 20px; color: #8fa0c2; }
    .uc-body { padding: 14px 16px 20px; max-width: 720px; margin: 0 auto; }
    .uc-card { background: rgba(16,28,56,0.72); border: 1px solid rgba(120,170,255,0.16); border-radius: 14px; box-shadow: 0 12px 34px rgba(0,0,0,0.35); }
    .uc-account { display: flex; align-items: center; gap: 14px; padding: 18px 16px; cursor: pointer; }
    .uc-avatar { flex-shrink: 0; }
    .uc-info { flex: 1; min-width: 0; }
    .uc-name { font-size: 18px; font-weight: 700; color: #eef3ff; margin-bottom: 4px; }
    .uc-meta { font-size: 12.5px; color: #8fa0c2; line-height: 1.6; }
    .uc-arrow { color: #5f7399; font-size: 16px; }
    .uc-section-title { margin: 18px 4px 10px; font-size: 14px; font-weight: 600; color: #8fa0c2; letter-spacing: 1px; }
    .uc-wallet { padding: 18px 16px; }
    .uc-wallet-top { display: flex; align-items: baseline; justify-content: space-between; }
    .uc-wallet-label { font-size: 13px; color: #8fa0c2; }
    .uc-wallet-balance { font-size: 30px; font-weight: 800; color: #4da3ff; text-shadow: 0 0 20px rgba(77,163,255,0.35); }
    .uc-wallet-tip { margin-top: 6px; font-size: 12px; color: #5f7399; }
    .uc-wallet-ops { margin-top: 14px; }
    .uc-wallet-ops :deep(.van-field) { background: rgba(10,18,38,0.6); border: 1px solid rgba(120,170,255,0.16); border-radius: 10px; }
    .uc-wallet-ops :deep(.van-field__label) { color: #8fa0c2; }
    .uc-wallet-ops :deep(.van-field__control) { color: #e8eefc; }
    .uc-wallet-btns { display: flex; gap: 12px; margin-top: 12px; }
    .uc-actions { margin-top: 18px; display: flex; flex-direction: column; gap: 12px; }
</style>
