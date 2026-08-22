import request from './request'

/** query merchant sandbox account (shown in admin personal center) */
export function getSandboxMerchant() {
    return request.get('/pay/sandbox/merchant')
}

/** reset sandbox balances to initial value */
export function resetSandbox() {
    return request.post('/pay/sandbox/reset')
}
