const TOKEN_KEY = 'token'

export function getToken() {
    return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
    localStorage.removeItem(TOKEN_KEY)
}

/**
 * \u89e3\u6790 JWT \u4e2d\u7684\u89d2\u8272\uff08\u65e0\u9700\u9a8c\u7b7e\uff0c\u4ec5\u7528\u4e8e\u524d\u7aef\u8def\u7531\u5224\u65ad\uff09
 * \u8fd4\u56de 'admin' | 'user' | null
 */
export function getTokenRole() {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return null
    try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(decodeURIComponent(escape(window.atob(base64))))
        return payload.role || null
    } catch (e) {
        return null
    }
}
