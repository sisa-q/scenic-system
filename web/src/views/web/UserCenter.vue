<template>
    <div class="user-center page-container">
        <van-cell-group inset>
            <van-cell title="头像" is-link @click="goProfile">
                <template #icon>
                    <van-image round width="40" height="40" :src="userInfo.avatar || 'https://img.yzcdn.cn/vant/cat.jpeg'" fit="cover" />
                </template>
            </van-cell>
            <van-cell title="昵称" :value="userInfo.nickname || '未设置'" is-link @click="goProfile" />
            <van-cell title="手机号" :value="userInfo.phone || '未绑定'" is-link @click="goProfile" />
            <van-cell title="角色" :value="userInfo.role === 'admin' ? '管理员' : '普通游客'" />
        </van-cell-group>

        <van-cell-group inset title="买家信息">
            <van-cell title="买家账号" :value="buyer.account || '-'" />
            <van-cell title="登录密码" :value="buyer.password || '111111'" />
            <van-cell title="支付密码" :value="buyer.payPassword || '111111'" />
            <van-cell title="用户 UID" :value="buyer.pidUid || '-'" />
            <van-cell title="用户名称" :value="buyer.userName || buyer.account || '-'" />
            <van-cell title="证件类型" :value="buyer.idType || 'IDENTITY_CARD'" />
            <van-cell title="证件账号" :value="buyer.idNo || '-'" />
            <van-cell title="账户余额" :value="'￥' + fmt(buyer.balance)" />
        </van-cell-group>

        <van-cell-group inset>
            <van-field v-model="amount" type="number" label="金额" placeholder="请输入金额" />
        </van-cell-group>
        <div style="padding: 0 16px; margin-bottom: 12px; display: flex; gap: 12px;">
            <van-button type="primary" block round @click="handleBalance('recharge')">充值</van-button>
            <van-button type="danger" block round plain @click="handleBalance('withdraw')">取现</van-button>
        </div>

        <div style="padding: 16px; display: flex; flex-direction: column; gap: 12px">
            <van-button type="default" block round @click="handleLogout">退出登录</van-button>
            <van-button type="danger" block round plain @click="handleDeleteAccount">注销账号</van-button>
        </div>

        <TabBar />
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { deleteAccount } from '@/api/user'
    import { getSandboxBuyer, sandboxRecharge, sandboxWithdraw } from '@/api/pay'
    import TabBar from '@/components/web/TabBar.vue'
    import { showConfirmDialog, showToast } from 'vant'

    export default {
        name: 'UserCenter',
        components: { TabBar },
        data() {
            return {
                refreshTimer: null,
                buyer: {},
                amount: 100
            }
        },
        computed: {
            userInfo() {
                return useUserStore().userInfo || {}
            }
        },
        methods: {
            fmt(v) {
                return Number(v || 0).toFixed(2)
            },
            async loadBuyer() {
                try {
                    const res = await getSandboxBuyer()
                    this.buyer = res.data || {}
                } catch (e) {
                    console.error('load buyer sandbox account failed', e)
                }
            },
            async handleBalance(action) {
                const amt = Number(this.amount)
                if (!amt || amt <= 0) {
                    showToast('请输入有效金额')
                    return
                }
                try {
                    const fn = action === 'recharge' ? sandboxRecharge : sandboxWithdraw
                    await fn({ role: 'buyer', amount: amt })
                    showToast(action === 'recharge' ? '充值成功' : '取现成功')
                    this.loadBuyer()
                } catch (e) {
                    // error toast handled by interceptor
                }
            },
            // ====== 个人账号数据实时同步：自动轮询 ======
            startAutoRefresh() {
                this.stopAutoRefresh()
                this.refreshTimer = setInterval(async () => {
                    try {
                        await useUserStore().getUserInfo({ silent: true })
                    } catch (e) {
                        // 静默失败，避免频繁弹窗
                    }
                }, 5000)
            },
            stopAutoRefresh() {
                if (this.refreshTimer) {
                    clearInterval(this.refreshTimer)
                    this.refreshTimer = null
                }
            },
            onVisibilityChange() {
                if (!document.hidden) {
                    useUserStore().getUserInfo({ silent: true }).catch(() => {})
                }
            },
            goProfile() {
                const token = localStorage.getItem('token')
                if (!token) {
                    showToast('请先登录')
                    this.$router.replace('/login')
                    return
                }
                this.$router.push('/profile')
            },
            async handleLogout() {
                try {
                    await showConfirmDialog({ title: '提示', message: '确认退出登录？' })
                    const store = useUserStore()
                    store.logout()
                    showToast('已退出登录')
                    // ✅ 跳转到统一登录页
                    this.$router.replace('/login')
                } catch (e) {}
            },
            async handleDeleteAccount() {
                try {
                    await showConfirmDialog({
                        title: '⚠️ 危险操作',
                        message: '确认注销账号？此操作不可恢复！',
                        confirmButtonText: '确认注销',
                        confirmButtonColor: '#ee0a24'
                    })
                    await showConfirmDialog({
                        title: '⚠️ 再次确认',
                        message: '真的要注销账号吗？',
                        confirmButtonText: '确定注销',
                        confirmButtonColor: '#ee0a24'
                    })
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
            if (!token) {
                this.$router.replace('/login')
                return
            }
            // 个人账号数据实时同步：每 5 秒静默刷新用户信息
            this.loadBuyer()
            this.startAutoRefresh()
            document.addEventListener('visibilitychange', this.onVisibilityChange)
        },
        // 页面卸载时停止轮询
        beforeUnmount() {
            this.stopAutoRefresh()
            document.removeEventListener('visibilitychange', this.onVisibilityChange)
        },
    }
</script>

<style scoped>
    .user-center {
        padding-bottom: 60px;
        min-height: 100vh;
        background: transparent;
    }
</style>
