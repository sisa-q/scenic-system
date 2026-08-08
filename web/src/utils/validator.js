/**
 * 校验手机号
 */
export function isValidPhone(phone) {
    return /^1[3-9]\d{9}$/.test(phone)
}

/**
 * 校验密码（至少6位）
 */
export function isValidPassword(password) {
    return password && password.length >= 6
}

/**
 * 校验邮箱
 */
export function isValidEmail(email) {
    return /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)
}

/**
 * 校验身份证号（简单校验）
 */
export function isValidIdCard(idCard) {
    return /^\d{17}[\dXx]$/.test(idCard)
}

/**
 * 校验是否为空
 */
export function isNotEmpty(value) {
    if (value === null || value === undefined) return false
    if (typeof value === 'string') return value.trim().length > 0
    if (Array.isArray(value)) return value.length > 0
    return true
}

/**
 * 校验数字是否在范围内
 */
export function isInRange(value, min, max) {
    return value >= min && value <= max
}