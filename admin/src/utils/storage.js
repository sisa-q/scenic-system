/**
 * 获取 localStorage 数据
 * @param {string} key
 */
export function getStorage(key) {
    try {
        const value = localStorage.getItem(key)
        return value ? JSON.parse(value) : null
    } catch {
        return localStorage.getItem(key)
    }
}

/**
 * 设置 localStorage 数据
 */
export function setStorage(key, value) {
    if (typeof value === 'object') {
        localStorage.setItem(key, JSON.stringify(value))
    } else {
        localStorage.setItem(key, value)
    }
}

/**
 * 移除 localStorage 数据
 */
export function removeStorage(key) {
    localStorage.removeItem(key)
}

/**
 * 清空所有 localStorage 数据
 */
export function clearStorage() {
    localStorage.clear()
}

/**
 * 获取 sessionStorage 数据
 */
export function getSession(key) {
    try {
        const value = sessionStorage.getItem(key)
        return value ? JSON.parse(value) : null
    } catch {
        return sessionStorage.getItem(key)
    }
}

/**
 * 设置 sessionStorage 数据
 */
export function setSession(key, value) {
    if (typeof value === 'object') {
        sessionStorage.setItem(key, JSON.stringify(value))
    } else {
        sessionStorage.setItem(key, value)
    }
}

/**
 * 移除 sessionStorage 数据
 */
export function removeSession(key) {
    sessionStorage.removeItem(key)
}