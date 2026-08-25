import request from './request'

/** 游客问答 Agent */
export function agentChat(question) {
    return request.post('/agent/chat', { question })
}
