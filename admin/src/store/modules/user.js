import { defineStore } from 'pinia'
import { loginApi, getUserInfoApi, logoutApi } from '@/api/user'

export const useUserStore = defineStore('user', {
    state: () => ({
        token: localStorage.getItem('token') || '',
        userInfo: {}
    }),
    actions: {
        async login(credentials) {
            const res = await loginApi(credentials)
            this.token = res.data.token
            localStorage.setItem('token', this.token)
            if (res.data.role) {
                localStorage.setItem('userRole', res.data.role)
            }
            return res
        },
        async getUserInfo() {
            const res = await getUserInfoApi()
            this.userInfo = res.data || {}
            if (this.userInfo.role) {
                localStorage.setItem('userRole', this.userInfo.role)
            }
            return res
        },
        async logout() {
            try {
                await logoutApi()
            } catch (e) {
                // 忽略：本地清理兜底
            }
            this.token = ''
            this.userInfo = {}
            localStorage.removeItem('token')
            localStorage.removeItem('userRole')
        }
    }
})