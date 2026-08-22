<template>
    <div class="login-wrapper">
        <div class="login-box">
            <h2>智慧景区</h2>
            <div class="tab-switch">
                <span :class="{ active: isLoginMode }" @click="isLoginMode = true">登录</span>
                <span :class="{ active: !isLoginMode }" @click="isLoginMode = false">注册</span>
            </div>

            <div v-if="isLoginMode">
                <van-field
                        v-model="loginForm.username"
                        label="账号"
                        placeholder="用户名或手机号"
                        type="text"
                />
                <van-field
                        v-model="loginForm.password"
                        label="密码"
                        placeholder="密码（至少 6 位）"
                        type="password"
                />
                <div style="margin: 16px 0">
                    <van-button type="primary" block round @click="onLogin" :loading="loginLoading">
                        {{ loginLoading ? '登录中...' : '登录' }}
                    </van-button>
                </div>
            </div>

            <div v-else>
                <van-field
                        v-model="registerForm.username"
                        label="用户名"
                        placeholder="4-20位字母或数字"
                        type="text"
                />
                <van-field
                        v-model="registerForm.password"
                        label="密码"
                        placeholder="至少 6 位"
                        type="password"
                />
                <div style="margin: 16px 0">
                    <van-button type="primary" block round @click="onRegister" :loading="registerLoading">
                        {{ registerLoading ? '注册中...' : '立即注册' }}
                    </van-button>
                </div>
                <div class="switch-hint">
                    已有账号？
                    <span @click="isLoginMode = true">去登录</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { registerApi } from '@/api/user'
    import { showToast } from 'vant'

    export default {
        name: 'WebLogin',
        data() {
            return {
                isLoginMode: true,
                loginLoading: false,
                registerLoading: false,
                loginForm: { username: '', password: '' },
                registerForm: { username: '', password: '' }
            }
        },
        methods: {
            async onLogin() {
                if (!this.loginForm.username || !this.loginForm.username.trim()) {
                    showToast('请输入用户名或手机号')
                    return
                }
                if (!this.loginForm.password || this.loginForm.password.length < 6) {
                    showToast('密码至少 6 位')
                    return
                }
                this.loginLoading = true
                try {
                    const store = useUserStore()
                    await store.login({
                        username: this.loginForm.username.trim(),
                        password: this.loginForm.password,
                        end: 'tourist'
                    })
                    await store.getUserInfo()
                    const role = store.userInfo?.role || 'user'
                    if (role === 'admin') {
                        store.logout()
                        showToast('管理员账号请在管理端登录')
                        this.loginForm.password = ''
                        return
                    }
                    showToast('登录成功')
                    this.$router.replace('/home')
                } catch (e) {
                    showToast(e.msg || e.message || '登录失败')
                } finally {
                    this.loginLoading = false
                }
            },
            async onRegister() {
                const username = this.registerForm.username.trim()
                const password = this.registerForm.password
                if (!username || username.length < 4 || username.length > 20) {
                    showToast('用户名4-20位字母或数字')
                    return
                }
                if (!/^[a-zA-Z0-9_]+$/.test(username)) {
                    showToast('用户名只能包含字母、数字、下划线')
                    return
                }
                if (!password || password.length < 6) {
                    showToast('密码至少 6 位')
                    return
                }
                this.registerLoading = true
                try {
                    await registerApi({ username, password, nickname: username, role: 'user' })
                    showToast('注册成功，请登录')
                    this.loginForm.username = username
                    this.isLoginMode = true
                    this.registerForm = { username: '', password: '' }
                } catch (e) {
                    showToast(e.msg || e.message || '注册失败')
                } finally {
                    this.registerLoading = false
                }
            }
        }
    }
</script>

<style scoped>
    .login-wrapper {
        position: relative;
        width: 100vw;
        height: 100vh;
        overflow: hidden;
        display: flex;
        align-items: center;
        justify-content: center;
        background:
            radial-gradient(circle at 20% 20%, rgba(64, 158, 255, 0.35), transparent 45%),
            radial-gradient(circle at 80% 80%, rgba(94, 234, 212, 0.18), transparent 45%),
            linear-gradient(135deg, #0b1b33 0%, #132e52 55%, #0d2240 100%);
    }
    .login-box {
        position: relative;
        z-index: 10;
        width: 420px;
        max-width: 92%;
        padding: 40px 30px;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.96);
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
    }
    .login-box h2 {
        margin: 0 0 24px;
        text-align: center;
        font-size: 24px;
        font-weight: 700;
        color: #1f2d3d;
        letter-spacing: 3px;
    }
    .tab-switch {
        display: flex;
        justify-content: center;
        gap: 40px;
        margin-bottom: 24px;
    }
    .tab-switch span {
        font-size: 17px;
        font-weight: 500;
        color: #909399;
        cursor: pointer;
        padding-bottom: 4px;
        transition: all 0.3s;
    }
    .tab-switch span.active {
        color: #409eff;
        border-bottom: 2px solid #409eff;
    }
    .switch-hint {
        margin-top: 12px;
        text-align: center;
        font-size: 14px;
        color: #909399;
    }
    .switch-hint span {
        color: #409eff;
        cursor: pointer;
        text-decoration: underline;
    }
    @media (max-width: 480px) {
        .login-box {
            padding: 28px 18px;
        }
    }
</style>
