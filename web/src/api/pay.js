import request from './request'

/** query buyer sandbox account (shown in tourist personal center) */
export function getSandboxBuyer() {
    return request.get('/pay/sandbox/buyer')
}

/** recharge buyer sandbox balance */
export function sandboxRecharge(data) {
    return request.post('/pay/sandbox/recharge', data)
}

/** withdraw buyer sandbox balance */
export function sandboxWithdraw(data) {
    return request.post('/pay/sandbox/withdraw', data)
}
