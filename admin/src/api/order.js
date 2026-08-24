import request from './request'

export function getOrderList(params) {
    return request.get('/order/list', { params })
}

export function getOrderDetail(id) {
    return request.get(`/order/detail/${id}`)
}

export function createOrder(data) {
    return request.post('/order/create', data)
}

export function payOrder(id) {
    return request.post(`/order/pay/${id}`)
}

// 游客端申请退款：仅提交申请，等待管理员审核（绝不自动退款）
export function applyRefund(id) {
    return request.post(`/order/refund-apply/${id}`)
}

// 管理员退款：仅管理员端点击“退款”时调用
export function refundOrder(id) {
    return request.post(`/order/refund/${id}`)
}

// 取消退款申请（游客或管理员）
export function cancelRefund(id) {
    return request.post(`/order/cancel-refund/${id}`)
}

export function batchDeleteOrders(ids) {
    return request.delete('/order/batch-delete', { data: { ids } })
}

// 一键清空订单数据（管理员）
export function clearOrderData() {
    return request.post('/order/clear')
}

// ✅ 新增：游客隐藏订单（批量）
export function hideOrders(ids) {
    return request.put('/order/hide', { ids })
}