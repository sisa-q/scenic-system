import request from './request'

/**
 * 获取景点列表
 * @param {Object} params - 分页参数等
 */
export function getSpotList(params) {
    return request.get('/spot/list', { params })
}

/**
 * 获取单个景点详情
 * @param {Number|String} id
 */
export function getSpotDetail(id) {
    return request.get(`/spot/detail/${id}`)
}

/**
 * 新增景点
 */
export function saveSpot(data) {
    return request.post('/spot/add', data)
}

/**
 * 更新景点
 */
export function updateSpot(data) {
    return request.put('/spot/update', data)
}

/**
 * 删除景点
 * @param {Number|String} id
 */
export function deleteSpot(id) {
    return request.delete(`/spot/delete/${id}`)
}