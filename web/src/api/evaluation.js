import request from '@/api/request'

// ---------- 游客端 ----------
/**
 * 提交评价（首次）
 */
export function submitEvaluation(data) {
    return request.post('/evaluation/submit', data)
}

/**
 * 更新评价
 */
export function updateEvaluation(data) {
    return request.put('/evaluation/update', data)
}

/**
 * 获取某个订单的评价
 */
export function getOrderEvaluation(orderId) {
    return request.get('/evaluation/order', { params: { orderId } })
}

// ---------- 管理员端 ----------
/**
 * 获取评价列表（分页 + 筛选）
 */
export function getEvaluationList(params) {
    return request.get('/evaluation/list', { params })
}

/**
 * 删除评价
 */
export function deleteEvaluation(id) {
    return request.delete(`/evaluation/${id}`)
}