<template>
    <div class="admin-page notice-manage-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">公告发布</div>
                <div class="page-subtitle">发布与维护景区通知公告</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">发布公告</el-button>
            </div>
        </div>
        <div class="notice-table-wrap">
        <el-table :data="tableData" height="100%" style="margin-top:0;" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="publishTime" label="发布时间" width="180" />
            <el-table-column prop="status" label="状态">
                <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                        {{ row.status === 1 ? '已发布' : '已下架' }}
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
        <div style="display:flex;justify-content:flex-end;padding:10px 4px 0;">
            <el-pagination
                    background
                    layout="total, prev, pager, next, sizes"
                    :total="total"
                    :current-page="page"
                    :page-size="size"
                    :page-sizes="[10, 20, 50]"
                    @current-change="handlePageChange"
                    @size-change="handleSizeChange"
            />
        </div>
        </div>

        <el-dialog
                v-model="dialogVisible"
                class="notice-dialog"
                :title="form.id ? '编辑公告' : '发布公告'"
                width="min(960px, 94vw)"
                align-center
        >
            <el-form :model="form" label-width="80px" class="notice-form">
                <el-form-item label="标题"><el-input v-model="form.title" placeholder="请输入公告标题" /></el-form-item>
                <el-form-item label="内容" class="notice-content-item">
                    <el-input v-model="form.content" type="textarea" :rows="18" resize="none" placeholder="请输入公告内容" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="发布" inactive-text="下架" />
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
    import { getNoticeList, saveNotice, updateNotice, deleteNotice } from '@/api/notice'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'NoticeManage',
        data() {
            return {
                tableData: [],
                page: 1,
                size: 10,
                total: 0,
                dialogVisible: false,
                form: { title: '', content: '', status: 1 }
            }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                try {
                    const res = await getNoticeList({ page: this.page, size: this.size })
                    const d = res.data || {}
                    this.tableData = (d.list !== undefined ? d.list : d) || []
                    this.total = d.total !== undefined ? Number(d.total) : this.tableData.length
                } catch (e) {
                    console.error('获取公告失败:', e)
                }
            },
            handlePageChange(p) {
                this.page = p
                this.fetchList()
            },
            handleSizeChange(s) {
                this.size = s
                this.page = 1
                this.fetchList()
            },
            handleAdd() {
                this.form = { title: '', content: '', status: 1 }
                this.dialogVisible = true
            },
            handleEdit(row) {
                this.form = { ...row }
                this.dialogVisible = true
            },
            async handleSave() {
                if (this.form.id) {
                    await updateNotice(this.form)
                } else {
                    await saveNotice(this.form)
                }
                ElMessage.success('保存成功')
                this.dialogVisible = false
                this.fetchList()
            },
            handleDelete(id) {
                ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' }).then(async () => {
                    await deleteNotice(id)
                    ElMessage.success('删除成功')
                    this.fetchList()
                }).catch(() => {})
            }
        }
    }
</script>

<style scoped>
    /* 页面铺满主内容区：页头固定，表格自动填充剩余高度并内部滚动 */
    .notice-manage-page {
        height: 100%;
        display: flex;
        flex-direction: column;
        overflow: hidden;
    }
    .notice-manage-page .page-header {
        flex-shrink: 0;
    }
    .notice-table-wrap {
        flex: 1;
        min-height: 0;
        overflow: hidden;
        display: flex;
        flex-direction: column;
    }
    .notice-table-wrap .el-table {
        height: 100% !important;
    }
</style>

<!-- 公告弹窗：铺满可用空间，内容完整显示（弹窗渲染到 body，需全局样式） -->
<style>
.notice-dialog {
    height: 86vh;
}
.notice-dialog .el-dialog__body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
}
.notice-dialog .notice-form {
    flex: 1;
    display: flex;
    flex-direction: column;
}
.notice-dialog .notice-content-item {
    flex: 1;
    display: flex;
    margin-bottom: 0;
    min-height: 160px;
}
.notice-dialog .notice-content-item .el-form-item__content {
    flex: 1;
    display: flex;
    align-items: stretch;
}
.notice-dialog .notice-content-item .el-textarea {
    flex: 1;
    display: flex;
}
.notice-dialog .notice-content-item .el-textarea__inner {
    flex: 1;
    height: 100% !important;
    resize: none;
}
</style>
