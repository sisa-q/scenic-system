import router from '@/router'
import { useAgentStore } from '@/store/modules/agent'

/**
 * AI 页面动作调度器：把后端下发的 actions（op/payload）翻译成真实页面操作。
 * Phase 1 动作白名单：goto_orders / open_ticket_form / focus_spot
 */
export function executeAgentActions(actions) {
    if (!Array.isArray(actions) || actions.length === 0) return
    for (const a of actions) {
        try {
            dispatchAction(a.op, a.payload || {})
        } catch (e) {
            console.error('[agent-action] 执行失败', a.op, e)
        }
    }
}

function dispatchAction(op, payload) {
    const store = useAgentStore()
    switch (op) {
        case 'goto_orders': {
            if (router.currentRoute.value.path !== '/orders') router.push('/orders')
            break
        }
        case 'open_ticket_form': {
            const spotId = payload.spotId || 1
            const slotId = payload.slotId
            if (slotId) {
                const query = { slotId, spotId }
                if (payload.quantity) query.quantity = payload.quantity
                if (payload.policyId) query.policyId = payload.policyId
                router.push({ path: '/order-confirm', query })
            } else {
                router.push('/spot/' + spotId)
            }
            break
        }
        case 'focus_spot': {
            const name = payload.spot || '故宫'
            if (router.currentRoute.value.path !== '/home') router.push('/home')
            // 等待首页挂载后再聚焦（Home.vue 会注册 sceneApi）
            setTimeout(() => {
                if (store.sceneApi && typeof store.sceneApi.focusSpot === 'function') {
                    store.sceneApi.focusSpot(name)
                }
            }, 450)
            break
        }
        default:
            console.warn('[agent-action] 未知动作', op, payload)
    }
}
