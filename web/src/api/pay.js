import request from './request'

/** query buyer sandbox account (shown in tourist personal center) */
export function getSandboxBuyer() {
    return request.get('/pay/sandbox/buyer')
}
