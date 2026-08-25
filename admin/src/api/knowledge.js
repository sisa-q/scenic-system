import request from './request'

/** 获取知识库文档列表 */
export function getKnowledgeList() {
    return request.get('/knowledge/list')
}

/** 新增知识文档 */
export function saveKnowledge(data) {
    return request.post('/knowledge/add', data)
}

/** 更新知识文档 */
export function updateKnowledge(data) {
    return request.put('/knowledge/update', data)
}

/** 删除知识文档 */
export function deleteKnowledge(id) {
    return request.delete('/knowledge/delete/' + id)
}
