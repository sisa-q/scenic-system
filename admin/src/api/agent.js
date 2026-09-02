import request from './request'

/** 智能体对话（可指定角色：tourist / ops / emergency）；LLM 推理较慢，单独放宽超时 */
export function agentChat(question, sessionId, role) {
    return request.post('/agent/chat', { question, sessionId, role }, { timeout: 300000 })
}

/** 确认危险操作（写操作） */
export function agentConfirm(question, action, params, sessionId, role) {
    return request.post('/agent/confirm', { question, action, params, sessionId, role }, { timeout: 300000 })
}
