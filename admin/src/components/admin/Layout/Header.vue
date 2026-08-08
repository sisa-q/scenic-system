<template>
    <div class="header-wrapper">
        <div class="breadcrumb">
            <span class="crumb-dot"></span>
            <span class="crumb-title">{{ $route.meta.title || '首页' }}</span>
        </div>
        <div class="user-info">
            <div class="avatar">{{ (userInfo.nickname || '管')[0] }}</div>
            <span class="uname">{{ userInfo.nickname || '管理员' }}</span>
            <el-button class="logout-btn" size="small" plain @click="handleLogout">退出</el-button>
        </div>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { ElMessageBox } from 'element-plus'

    export default {
        name: 'Header',
        computed: {
            userInfo() {
                return useUserStore().userInfo
            }
        },
        methods: {
            handleLogout() {
                ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
                    .then(() => {
                        const store = useUserStore()
                        store.logout()
                        // ✨ 跳转到统一登录页
                        window.location.href = '/login'
                    })
                    .catch(() => {})
            }
        }
    }
</script>

<style scoped>
    .header-wrapper {
        width: 100%;
        display: flex;
        justify-content: space-between;
        align-items: center;
    }
    .breadcrumb {
        display: flex;
        align-items: center;
        gap: 10px;
    }
    .crumb-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: linear-gradient(135deg, #e5c97b, #9c7a2e);
        box-shadow: 0 0 8px rgba(200, 162, 74, 0.6);
    }
    .crumb-title {
        font-size: 18px;
        font-weight: 700;
        letter-spacing: 2px;
        color: #16283f;
        font-family: 'STKaiti', 'KaiTi', 'SimSun', serif;
    }
    .user-info {
        display: flex;
        align-items: center;
        gap: 12px;
    }
    .avatar {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        font-weight: 700;
        font-size: 14px;
        background: linear-gradient(135deg, #3a6ec5, #2456a8);
        box-shadow: 0 3px 10px rgba(36, 86, 168, 0.3);
    }
    .uname {
        color: #4a5b78;
        font-weight: 500;
    }
    .logout-btn {
        border-radius: 8px;
    }
</style>