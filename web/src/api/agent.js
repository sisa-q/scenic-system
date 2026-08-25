import request from './request'

/** 游客问答 Agent（可调用工具） */
export function agentChat(question, sessionId) {
    return request.post('/agent/chat', { question, sessionId })
}

/** 确认危险操作并执行 */
export function agentConfirm(question, action, params, sessionId) {
    return request.post('/agent/confirm', { question, action, params, sessionId })
}
