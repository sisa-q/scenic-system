import axios from 'axios'
import { showToast } from 'vant'

// 创建 axios 实例
const request = axios.create({
    baseURL: process.env.VUE_APP_BASE_API || '/api',
    timeout: 10000
})

// 请求拦截器：自动携带 token
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = 'Bearer ' + token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// ====== 统一错误处理 ======
function handleAuthExpired() {
    localStorage.removeItem('token')
    localStorage.removeItem('userRole')
    if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
    }
}

// 响应拦截器：统一处理业务码。传 { silent: true } 时静默（轮询自动刷新使用，避免频繁弹窗）
request.interceptors.response.use(
    response => {
        const res = response.data
        const silent = response.config && response.config.silent
        if (res.code === 401) {
            handleAuthExpired()
            return Promise.reject(res)
        }
        if (res.code === 403) {
            if (!silent) showToast(res.msg || '无权限访问')
            return Promise.reject(res)
        }
        if (res.code !== 200) {
            if (!silent) showToast(res.msg || '请求失败')
            return Promise.reject(res)
        }
        return res
    },
    error => {
        const status = error.response?.status
        const msg = error.response?.data?.msg
        const silent = error.config && error.config.silent
        if (status === 401) {
            handleAuthExpired()
        } else if (status === 403) {
            if (!silent) showToast(msg || '无权限访问')
        } else {
            if (!silent) showToast(msg || error.message || '网络异常')
        }
        return Promise.reject(error)
    }
)

export default request
