import router from '@/router'
import { useAgentStore } from '@/store/modules/agent'

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

/**
 * AI 页面动作调度器（逐步执行 + 过程可视化）
 * 动作：goto_orders / focus_spot / goto_spot / switch_tab / select_slot
 * 页面联动通过 window CustomEvent（目标组件自行监听，模板/CSS 零侵入）
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
        await sleep(600)
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
            // 回到首页，调用首页已注册的搜索定位能力（复用搜索框逻辑）
            const name = payload.spot || '故宫'
            if (router.currentRoute.value.path !== '/home') await router.push('/home')
            await sleep(400)
            if (store.sceneApi && typeof store.sceneApi.focusSpot === 'function') {
                store.sceneApi.focusSpot(name)
            }
            await sleep(1100) // 让地球旋转定位动画可见
            break
        }
        case 'goto_spot': {
            const spotId = payload.spotId || 1
            await router.push('/spot/' + spotId)
            await sleep(1600) // 等景点页组件加载 + 时段拉取完成
            break
        }
        case 'switch_tab': {
            // 通知景点页切换到指定 tab（购票选择）
            window.dispatchEvent(new CustomEvent('agent:switch-tab', { detail: { tab: payload.tab || 'ticket' } }))
            await sleep(700)
            break
        }
        case 'select_slot': {
            // 通知景点页高亮时段并自动跳转下单确认页（数量预填）
            window.dispatchEvent(new CustomEvent('agent:select-slot', {
                detail: { slotId: payload.slotId, quantity: payload.quantity || 1 }
            }))
            await sleep(1600) // 等页面高亮 + 自动跳转下单确认页
            break
        }
        default:
            console.warn('[agent-action] 未知动作', op, payload)
    }
}
