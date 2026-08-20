<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">景点管理</div>
                <div class="page-subtitle">维护园内景点信息（新数据在上，支持搜索与分类）</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">新增景点</el-button>
            </div>
        </div>

        <!-- 智能筛选工具栏 -->
        <div class="admin-toolbar">
            <el-input v-model="keyword" placeholder="搜索景点名称 / 位置" clearable />
            <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:130px;">
                <el-option label="开放" :value="1" />
                <el-option label="停用" :value="0" />
            </el-select>
            <span class="toolbar-count">共 {{ filteredList.length }} 条</span>
        </div>

        <div class="admin-list-card">
            <el-table :data="filteredList" border>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="location" label="位置" />
                <el-table-column prop="capacity" label="容量上限" />
                <el-table-column prop="status" label="状态">
                    <template #default="{ row }">
                        <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                            {{ row.status === 1 ? '开放' : '停用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                    <template #default="{ row }">
                        <el-button size="small" @click="handleEdit(row)">编辑</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </div>

        <el-dialog v-model="dialogVisible" :title="form.id ? '编辑景点' : '新增景点'" width="500px">
            <el-form :model="form" label-width="80px">
                <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
                <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
                <el-form-item label="容量上限"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
                <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="景点简介" /></el-form-item>
                <el-form-item label="图片URL"><el-input v-model="form.imageUrl" placeholder="https://..." /></el-form-item>
                <el-form-item label="状态">
                    <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleSave">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import { getSpotList, saveSpot, updateSpot, deleteSpot } from '@/api/spot'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'SpotManage',
        data() {
            return {
                tableData: [],
                keyword: '',
                statusFilter: undefined,
                dialogVisible: false,
                form: { name: '', location: '', description: '', imageUrl: '', capacity: 0, status: 1 }
            }
        },
        computed: {
            filteredList() {
                const kw = (this.keyword || '').trim().toLowerCase()
                return this.tableData.filter(row => {
                    if (this.statusFilter !== undefined && this.statusFilter !== null && row.status !== this.statusFilter) return false
                    if (!kw) return true
                    return [row.name, row.location, row.description].some(v => v && String(v).toLowerCase().includes(kw))
                })
            }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                try {
                    const res = await getSpotList()
                    // 新数据在上面
                    this.tableData = (res.data || []).slice().sort((a, b) => (b.id || 0) - (a.id || 0))
                } catch (e) {
                    console.error('获取景点失败:', e)
                }
            },
            handleAdd() {
                this.form = { name: '', location: '', description: '', imageUrl: '', capacity: 0, status: 1 }
                this.dialogVisible = true
            },
            handleEdit(row) {
                this.form = { ...row }
                this.dialogVisible = true
            },
            async handleSave() {
                try {
                    if (this.form.id) {
                        await updateSpot(this.form)
                    } else {
                        await saveSpot(this.form)
                    }
                    ElMessage.success('保存成功')
                    this.dialogVisible = false
                    this.fetchList()
                } catch (e) {
                    console.error('保存失败:', e)
                }
            },
            handleDelete(id) {
                ElMessageBox.confirm('确认删除该景点？', '提示', { type: 'warning' }).then(async () => {
                    await deleteSpot(id)
                    ElMessage.success('删除成功')
                    this.fetchList()
                }).catch(() => {})
            }
        }
    }
</script>
