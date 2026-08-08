import request from './request'

/**
 * 获取客流统计数据（大屏用）
 * @param {Object} params - { days, spotId 等 }
 */
export function getFlowStats(params) {
    return request.get('/flow/stats', { params })
}

/**
 * 获取实时客流数据（在园人数等）
 */
export function getRealTime() {
    return request.get('/flow/realtime')
}