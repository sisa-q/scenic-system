import request from './request'

/**
 * 获取核销记录列表（后台）
 * @param {Object} params - 分页参数
 */
export function getVerifyList(params) {
    return request.get('/verify/list', { params })
}

/**
 * 执行核销（扫码或手动输入）
 * @param {Object} data - { code: '核销码' }
 */
export function doVerify(data) {
    return request.post('/verify/execute', data)
}