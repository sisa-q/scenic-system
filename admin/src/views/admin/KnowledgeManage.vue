<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">知识库</div>
                <div class="page-subtitle">维护景区知识文档（AI 助手自动检索）</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">新增知识</el-button>
            </div>
        </div>

        <div class="admin-toolbar">
            <el-input v-model="keyword" placeholder="搜索标题" clearable />
            <span style="color:#8fa0c2;font-size:12px;">共 {{ filteredList.length }} 条</span>
        </div>

        <div class="admin-list-card">
            <el-table :data="filteredList" style="margin-top:0;" border>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="title" label="标题" />
                <el-table-column prop="updateTime" label="更新时间" width="180" />
                <el-table-column label="操作" width="160">
                    <template #default="{ row }">
                        <el-button size="small" @click="handleEdit(row)">编辑</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </div>

        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
            <el-form :model="form" label-width="70px">
                <el-form-item label="标题" required>
                    <el-input v-model="form.title" placeholder="请输入标题" />
                </el-form-item>
                <el-form-item label="内容" required>
                    <el-input v-model="form.content" type="textarea" :rows="10" placeholder="请输入内容" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import { getKnowledgeList, saveKnowledge, updateKnowledge, deleteKnowledge } from '@/api/knowledge'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'KnowledgeManage',
        data() {
            return {
                tableData: [],
                keyword: '',
                dialogVisible: false,
                saving: false,
                form: { id: null, title: '', content: '' }
            }
        },
        computed: {
            dialogTitle() { return this.form.id ? '编辑知识' : '新增知识' },
            filteredList() {
                const kw = (this.keyword || '').trim().toLowerCase()
                return this.tableData.filter(row => !kw || String(row.title || '').toLowerCase().includes(kw))
            }
        },
        mounted() { this.fetchList() },
        methods: {
            async fetchList() {
                try {
                    const res = await getKnowledgeList()
                    this.tableData = res.data || []
                } catch (e) { console.error('load knowledge failed', e) }
            },
            handleAdd() {
                this.form = { id: null, title: '', content: '' }
                this.dialogVisible = true
            },
            handleEdit(row) {
                this.form = { id: row.id, title: row.title, content: row.content }
                this.dialogVisible = true
            },
            async handleSave() {
                if (!this.form.title || !this.form.title.trim()) { ElMessage.warning('请输入标题'); return }
                if (!this.form.content || !this.form.content.trim()) { ElMessage.warning('请输入内容'); return }
                this.saving = true
                try {
                    if (this.form.id) { await updateKnowledge(this.form) } else { await saveKnowledge(this.form) }
                    ElMessage.success('保存成功')
                    this.dialogVisible = false
                    await this.fetchList()
                } catch (e) { console.error('save failed', e) } finally { this.saving = false }
            },
            handleDelete(id) {
                ElMessageBox.confirm('确认删除该知识文档？', '提示', { type: 'warning' })
                    .then(async () => {
                        await deleteKnowledge(id)
                        ElMessage.success('删除成功')
                        await this.fetchList()
                    })
                    .catch(() => {})
            }
        }
    }
</script>
