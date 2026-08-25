import router from '@/router'
import { useAgentStore } from '@/store/modules/agent'

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

/**
 * AI 页面动作调度器（逐步执行 + 过程可视化）
 * 动作：goto_orders / focus_spot / goto_spot / select_slot
 * @param actions 后端下发动作数组 [{op,label,payload}]
 * @param onStep  每步状态回调 (label, status: 'running'|'done'|'error'|'finished')
 */
export async function executeAgentActions(actions, { onStep } = {}) {
    if (!Array.isArray(actions) || actions.length === 0) return
    for (const a of actions) {
        const label = a.label || a.op
        if (onStep) onStep(label, 'running')
        try {
            await dispatchAction(a.op, a.payload || {})
        } catch (e) {
            console.error('[agent-action] 执行失败', a.op, e)
            if (onStep) onStep(label, 'error')
            continue
        }
        if (onStep) onStep(label, 'done')
        await sleep(700)
    }
    if (onStep) onStep('', 'finished')
}

async function dispatchAction(op, payload) {
    const store = useAgentStore()
    switch (op) {
        case 'goto_orders': {
            if (router.currentRoute.value.path !== '/orders') await router.push('/orders')
            await sleep(400)
            break
        }
        case 'focus_spot': {
            const name = payload.spot || '故宫'
            if (router.currentRoute.value.path !== '/home') await router.push('/home')
            await sleep(350)
            if (store.sceneApi && typeof store.sceneApi.focusSpot === 'function') {
                store.sceneApi.focusSpot(name)
            }
            await sleep(1000) // 让地球旋转定位动画可见
            break
        }
        case 'goto_spot': {
            const spotId = payload.spotId || 1
            const query = {}
            if (payload.tab) query.tab = payload.tab
            await router.push({ path: '/spot/' + spotId, query })
            await sleep(600)
            break
        }
        case 'select_slot': {
            // 通过路由 query 通知景点页自动选中时段并跳转下单确认页（数量预填）
            const spotId = payload.spotId || 1
            const query = { tab: 'ticket' }
            if (payload.slotId) query.slot = payload.slotId
            if (payload.quantity) query.qty = payload.quantity
            await router.replace({ path: '/spot/' + spotId, query })
            await sleep(1200) // 等待页面高亮时段并自动跳转下单确认页
            break
        }
        default:
            console.warn('[agent-action] 未知动作', op, payload)
    }
}
