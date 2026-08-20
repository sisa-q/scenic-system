import request from './request'

/**
 * 用户登录
 */
export function loginApi(data) {
    return request.post('/user/login', data)
}

/**
 * 获取当前用户信息
 */
export function getUserInfoApi(options) {
    return request.get('/user/info', { ...options })
}

/**
 * 用户注册
 */
export function registerApi(data) {
    return request.post('/user/register', data)
}

/**
 * 更新用户信息（昵称、手机号）
 */
export function updateProfile(data) {
    return request.put('/user/update', data)
}

/**
 * 注销账号
 */
export function deleteAccount() {
    return request.delete('/user/delete')
}

/**
 * 退出登录（服务端将 JWT 加入 Redis 黑名单，立即失效）
 */
export function logoutApi() {
    return request.post('/user/logout')
}
