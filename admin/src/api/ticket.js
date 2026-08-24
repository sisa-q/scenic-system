import request from './request'

// ============================================================
//  票种管理（Ticket Policy）
// ============================================================

/**
 * 获取票种列表
 * @param {Object} params - 查询参数
 * @param {number} params.spotId - 景点ID（可选）
 */
export function getTicketList(params) {
    return request.get('/ticket/list', { params })
}

/**
 * 新增票种
 */
export function saveTicket(data) {
    return request.post('/ticket/add', data)
}

/**
 * 更新票种
 */
export function updateTicket(data) {
    return request.put('/ticket/update', data)
}

/**
 * 删除票种
 */
export function deleteTicket(id) {
    return request.delete(`/ticket/delete/${id}`)
}

// ============================================================
//  分时时段管理（Time Slot）
// ============================================================

/**
 * 获取时段列表（管理端用）
 * @param {Object} params - 查询参数
 * @param {number} params.policyId - 票种ID（可选）
 */
export function getSlots(params) {
    return request.get('/ticket/slots', { params })
}

/**
 * 按景点ID获取所有时段（游客端用）
 */
export function getSlotsBySpot(spotId) {
    return request.get('/ticket/slots/spot', { params: { spotId } })
}

/**
 * ✅ 新增：根据时段ID获取单个时段详情（游客端订单确认页用）
 */
export function getSlotById(id) {
    return request.get(`/ticket/slot/${id}`)
}

/**
 * 新增时段
 */
export function saveSlot(data) {
    return request.post('/ticket/slot/add', data)
}

/**
 * 更新时段
 */
export function updateSlot(data) {
    return request.put('/ticket/slot/update', data)
}

/**
 * 删除时段
 */
export function deleteSlot(id) {
    return request.delete(`/ticket/slot/delete/${id}`)
}

/**
 * 批量删除时段
 */
export function batchDeleteSlots(ids) {
    return request.post('/ticket/slot/batch-delete', { ids })
}

// ============================================================
//  景点相关（游客端使用）
// ============================================================

/**
 * 获取景点列表（游客端首页）
 */
export function getSpotList() {
    return request.get('/spot/list')
}

/**
 * 获取景点详情（游客端详情页）
 */
export function getSpotDetail(id) {
    return request.get(`/spot/detail/${id}`)
}