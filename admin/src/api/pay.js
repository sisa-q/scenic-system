import request from './request'

/** 查询沙箱账户（商户/买家余额） */
export function getSandboxAccounts() {
    return request.get('/pay/sandbox/accounts')
}

/** 查询沙箱余额变动流水 */
export function getSandboxFlows() {
    return request.get('/pay/sandbox/flows')
}

/** 重置沙箱余额为初始值 */

/** 列表页兜底：对待支付订单批量主动查询支付宝并确认 */
export function refreshPendingPayments(options) {
    return request.post('/pay/refresh-pending', null, { ...options })
}
export function resetSandbox() {
    return request.post('/pay/sandbox/reset')
}
