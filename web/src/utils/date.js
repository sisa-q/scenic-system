import dayjs from 'dayjs'

/**
 * 格式化日期
 * @param {Date|string} date - 日期对象或字符串
 * @param {string} format - 格式模板，默认 'YYYY-MM-DD HH:mm:ss'
 */
export function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
    if (!date) return ''
    return dayjs(date).format(format)
}

/**
 * 格式化日期（短格式：YYYY-MM-DD）
 */
export function formatDateShort(date) {
    if (!date) return ''
    return dayjs(date).format('YYYY-MM-DD')
}

/**
 * 获取当前时间
 */
export function getNow() {
    return dayjs().format('YYYY-MM-DD HH:mm:ss')
}

/**
 * 相对时间（如：3分钟前、2小时前）
 */
export function timeAgo(date) {
    if (!date) return ''
    const diff = dayjs().diff(dayjs(date), 'second')
    if (diff < 60) return '刚刚'
    if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
    if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
    if (diff < 604800) return Math.floor(diff / 86400) + '天前'
    return formatDateShort(date)
}