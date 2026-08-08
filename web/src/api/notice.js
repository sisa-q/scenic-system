import request from './request'

/**
 * 获取公告列表
 */
export function getNoticeList() {
    return request.get('/notice/list')
}

/**
 * 获取公告详情
 * @param {Number|String} id
 */
export function getNoticeDetail(id) {
    return request.get(`/notice/detail/${id}`)
}

/**
 * 发布公告（后台）
 */
export function saveNotice(data) {
    return request.post('/notice/add', data)
}

/**
 * 更新公告
 */
export function updateNotice(data) {
    return request.put('/notice/update', data)
}

/**
 * 删除公告
 */
export function deleteNotice(id) {
    return request.delete(`/notice/delete/${id}`)
}