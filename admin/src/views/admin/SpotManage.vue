<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">景点管理</div>
                <div class="page-subtitle">维护园区景点基础信息与承载容量</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">新增景点</el-button>
            </div>
        </div>
        <el-table :data="tableData" style="margin-top:0;" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="location" label="位置" />
            <el-table-column prop="capacity" label="承载量" />
            <el-table-column prop="status" label="状态">
                <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                        {{ row.status === 1 ? '启用' : '停用' }}
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

        <el-dialog v-model="dialogVisible" :title="form.id ? '编辑景点' : '新增景点'" width="500px">
            <el-form :model="form" label-width="80px">
                <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
                <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
                <el-form-item label="承载量"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
                <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="景点简介" /></el-form-item>
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
                dialogVisible: false,
                form: { name: '', location: '', description: '', imageUrl: '', capacity: 0, status: 1 }
            }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                try {
                    const res = await getSpotList()
                    this.tableData = res.data || []
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
                    // 业务错误已由 request 拦截器统一提示，这里只记录日志，避免未捕获异常
                    console.error('保存失败:', e)
                }
            },
            handleDelete(id) {
                ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' }).then(async () => {
                    await deleteSpot(id)
                    ElMessage.success('删除成功')
                    this.fetchList()
                }).catch(() => {})
            }
        }
    }
</script>