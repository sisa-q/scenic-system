import request from './request'

/** query merchant sandbox account (shown in admin personal center) */
export function getSandboxMerchant() {
    return request.get('/pay/sandbox/merchant')
}

/** recharge merchant sandbox balance */
export function sandboxRecharge(data) {
    return request.post('/pay/sandbox/recharge', data)
}

/** withdraw merchant sandbox balance */
export function sandboxWithdraw(data) {
    return request.post('/pay/sandbox/withdraw', data)
}

/** reset sandbox balances to initial value */
export function resetSandbox() {
    return request.post('/pay/sandbox/reset')
}
