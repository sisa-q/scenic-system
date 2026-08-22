<template>
    <div class="login-page">
        <div class="login-bg"></div>
        <div class="login-card">
            <div class="login-header">
                <div class="login-logo">🏞️</div>
                <h1 class="login-title">智慧景区管理后台</h1>
                <p class="login-subtitle">DIGITAL SCENIC ADMINISTRATION</p>
            </div>

            <el-form :model="form" size="large" @keyup.enter="onLogin">
                <el-form-item>
                    <el-input v-model="form.username" placeholder="管理员账号" :prefix-icon="User" clearable />
                </el-form-item>
                <el-form-item>
                    <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" class="login-btn" :loading="loginLoading" @click="onLogin">
                        {{ loginLoading ? '登录中...' : '登录' }}
                    </el-button>
                </el-form-item>
            </el-form>

            <div class="login-footer">
                <span class="admin-only">仅限管理员登录</span>
                <el-link type="primary" :href="otherAppUrl + '/login'" target="_blank">游客入口</el-link>
            </div>
        </div>
    </div>
</template>

<script>
    import { User, Lock } from '@element-plus/icons-vue'
    import { ElMessage } from 'element-plus'
    import { useUserStore } from '@/store/modules/user'

    export default {
        name: 'AdminLogin',
        data() {
            return {
                form: { username: '', password: '' },
                loginLoading: false,
                otherAppUrl: process.env.VUE_APP_OTHER_APP_URL || 'http://localhost:8080'
            }
        },
        methods: {
            async onLogin() {
                if (!this.form.username || !this.form.username.trim()) {
                    ElMessage.warning('请输入管理员账号')
                    return
                }
                if (!this.form.password) {
                    ElMessage.warning('请输入密码')
                    return
                }
                this.loginLoading = true
                try {
                    const store = useUserStore()
                    await store.login({
                        username: this.form.username.trim(),
                        password: this.form.password,
                        end: 'admin'
                    })
                    await store.getUserInfo()
                    const role = store.userInfo?.role || 'user'
                    if (role === 'admin') {
                        ElMessage.success('登录成功')
                        this.$router.replace('/admin/dashboard')
                    } else {
                        store.logout()
                        ElMessage.error('游客账号请在游客端登录')
                        this.form.password = ''
                    }
                } catch (e) {
                    ElMessage.error(e.msg || e.message || '登录失败')
                } finally {
                    this.loginLoading = false
                }
            }
        }
    }
</script>

<style scoped>
    .login-page {
        position: relative;
        width: 100vw;
        height: 100vh;
        overflow: hidden;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #0b1b33 0%, #132e52 55%, #0d2240 100%);
    }
    .login-bg {
        position: absolute;
        inset: 0;
        background:
            radial-gradient(circle at 20% 20%, rgba(64, 158, 255, 0.35), transparent 45%),
            radial-gradient(circle at 80% 80%, rgba(94, 234, 212, 0.18), transparent 45%);
    }
    .login-card {
        position: relative;
        z-index: 1;
        width: 400px;
        max-width: 92%;
        padding: 40px 36px 28px;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.97);
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
    }
    .login-header { text-align: center; margin-bottom: 26px; }
    .login-logo { font-size: 40px; line-height: 1; margin-bottom: 8px; }
    .login-title { margin: 0 0 6px; font-size: 22px; font-weight: 700; color: #1f2d3d; letter-spacing: 2px; }
    .login-subtitle { margin: 0; font-size: 11px; letter-spacing: 3px; color: #909399; }
    .login-btn { width: 100%; margin-top: 4px; letter-spacing: 6px; font-weight: 600; }
    .login-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; font-size: 13px; color: #909399; }
    .admin-only { color: #c0c4cc; }
</style>
