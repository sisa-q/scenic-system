<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">票务策略</div>
                <div class="page-subtitle">配置各景点票种价格、库存与退改规则</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">新增票种</el-button>
            </div>
        </div>
        <!-- 智能筛选工具栏 -->
        <div class="admin-toolbar">
            <el-input v-model="keyword" placeholder="搜索票种 / 景点" clearable />
            <el-select v-model="spotFilter" placeholder="按景点分类" clearable style="width:150px;">
                <el-option v-for="item in spotOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <span style="color:#8fa0c2;font-size:12px;">共 {{ filteredList.length }} 条</span>
        </div>
<div class="table-wrapper admin-list-card">
            <el-table :data="filteredList" style="margin-top:0;" height="100%" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="spotName" label="所属景点" />
            <el-table-column prop="name" label="票种名称" />
            <el-table-column prop="price" label="价格" />
            <el-table-column prop="totalQuota" label="总库存" />
            <el-table-column prop="refundRule" label="退改规则" />
            <el-table-column label="操作" width="200">
                <template #default="{ row }">
                    <el-button size="small" @click="handleEdit(row)">编辑</el-button>
                    <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
                </template>
            </el-table-column>
            </el-table>
        </div>

        <el-dialog v-model="dialogVisible" :title="form.id ? '编辑票种' : '新增票种'" width="600px">
            <el-form :model="form" label-width="100px">
                <el-form-item label="所属景点">
                    <el-select v-model="form.spotId" placeholder="请选择">
                        <el-option v-for="item in spotOptions" :key="item.id" :label="item.name" :value="item.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="票种名称"><el-input v-model="form.name" /></el-form-item>
                <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
                <el-form-item label="总库存"><el-input-number v-model="form.totalQuota" :min="1" /></el-form-item>
                <el-form-item label="退改规则"><el-input v-model="form.refundRule" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleSave">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script>
    import { getTicketList, saveTicket, updateTicket, deleteTicket } from '@/api/ticket'
    import { getSpotList } from '@/api/spot'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'TicketPolicy',
        data() {
            return {
                tableData: [],
                keyword: '',
                spotFilter: undefined,
                spotOptions: [],
                dialogVisible: false,
                form: { spotId: '', name: '', price: 0, totalQuota: 0, refundRule: '' }
            }
        },
        computed: {
            filteredList() {
                const kw = (this.keyword || '').trim().toLowerCase()
                return this.tableData.filter(row => {
                    if (this.spotFilter !== undefined && this.spotFilter !== null && row.spotId !== this.spotFilter) return false
                    if (!kw) return true
                    return [row.name, row.spotName].some(v => v && String(v).toLowerCase().includes(kw))
                })
            }
        },
        mounted() {
            this.fetchList()
            this.fetchSpots()
        },
        methods: {
            async fetchList() {
                try {
                    const res = await getTicketList()
                    this.tableData = (res.data || []).slice().sort((a, b) => (b.id || 0) - (a.id || 0))
                } catch (e) {
                    console.error('获取票种失败:', e)
                }
            },
            async fetchSpots() {
                try {
                    const res = await getSpotList()
                    this.spotOptions = res.data || []
                } catch (e) {
                    console.error('获取景点失败:', e)
                }
            },
            handleAdd() {
                this.form = { spotId: '', name: '', price: 0, totalQuota: 0, refundRule: '' }
                this.dialogVisible = true
            },
            handleEdit(row) {
                this.form = { ...row }
                this.dialogVisible = true
            },
            async handleSave() {
                try {
                    if (this.form.id) {
                        await updateTicket(this.form)
                    } else {
                        await saveTicket(this.form)
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
                    await deleteTicket(id)
                    ElMessage.success('删除成功')
                    this.fetchList()
                }).catch(() => {})
            }
        }
    }
</script>

<style scoped>
    /* 票务策略页铺满主内容区：页面占满高度，表格占满剩余空间 */
    .admin-page {
        height: 100%;
        display: flex;
        flex-direction: column;
        overflow: hidden;
    }
    .table-wrapper {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }
    .table-wrapper :deep(.el-table) {
        flex: 1;
    }
</style>
