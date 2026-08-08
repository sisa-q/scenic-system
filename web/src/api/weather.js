import request from './request'

/**
 * 批量获取景点天气（游客端地球导览）
 * @param {Array} points - [{spotId, name, lat, lng}]
 */
export function getWeatherBatch(points) {
    return request.post('/weather/batch', points)
}

/**
 * 获取单个景点当前天气 + 分析 + 3天预报
 */
export function getWeatherNow(lat, lng, spotId, name) {
    return request.get('/weather/now', { params: { lat, lng, spotId, name } })
}