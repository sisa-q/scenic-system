<template>
    <div class="admin-page evaluation-manage">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">评价管理</div>
                <div class="page-subtitle">查看游客评价与情感得分分析</div>
            </div>
        </div>

        <!-- 搜索栏 -->
        <el-card class="search-card">
            <el-form :model="searchForm" label-width="80px" size="small" @submit.prevent="handleSearch">
                <el-row :gutter="16">
                    <el-col :span="6">
                        <el-form-item label="订单号">
                            <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
                        </el-form-item>
                    </el-col>
                    <el-col :span="6">
                        <el-form-item label="景点">
                            <el-input v-model="searchForm.spotName" placeholder="景点名称" clearable />
                        </el-form-item>
                    </el-col>
                    <el-col :span="6">
                        <el-form-item label="评分">
                            <el-select v-model="searchForm.rating" placeholder="全部" clearable>
                                <el-option v-for="i in 5" :key="i" :label="i + ' 星'" :value="i" />
                                <el-option label="未评分" :value="0" />
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <el-col :span="6">
                        <el-form-item label="时间">
                            <el-date-picker
                                    v-model="searchForm.dateRange"
                                    type="daterange"
                                    range-separator="至"
                                    start-placeholder="开始日期"
                                    end-placeholder="结束日期"
                                    value-format="YYYY-MM-DD"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="24" style="text-align: right;">
                        <el-button type="primary" size="small" @click="handleSearch">搜索</el-button>
                        <el-button size="small" @click="resetSearch">重置</el-button>
                    </el-col>
                </el-row>
            </el-form>
        </el-card>

        <!-- 表格 -->
        <el-card class="table-card">
            <el-table
                    :data="tableData"
                    v-loading="loading"
                    border
                    stripe
                    style="width: 100%"
            >
                <el-table-column prop="id" label="ID" width="70" align="center" />
                <el-table-column prop="orderNo" label="订单号" min-width="180" />
                <el-table-column prop="spotName" label="景点" min-width="120" />
                <el-table-column prop="rating" label="评分" width="150" align="center">
                    <template #default="{ row }">
                        <el-rate
                                :model-value="row.rating !== undefined && row.rating !== null ? row.rating : (row.score || 0)"
                                disabled
                                show-score
                                score-template="{value} 星"
                                text-color="#ff9900"
                                :colors="['#99A9BF', '#F7BA2A', '#FF9900']"
                        />
                    </template>
                </el-table-column>
                <el-table-column prop="content" label="评价内容" min-width="220" show-overflow-tooltip />
                <el-table-column prop="emotionScore" label="情感得分" width="120" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.emotionScore >= 0.5 ? 'success' : row.emotionScore >= 0 ? 'warning' : 'danger'" size="small">
                            {{ row.emotionScore !== undefined && row.emotionScore !== null ? row.emotionScore.toFixed(2) : '-' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="createTime" label="评价时间" width="170" align="center" />
                <el-table-column label="操作" width="100" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination-container">
                <el-pagination
                        background
                        layout="total, sizes, prev, pager, next, jumper"
                        :total="total"
                        v-model:page-size="pageSize"
                        v-model:current-page="currentPage"
                        :page-sizes="[10, 20, 50, 100]"
                        @size-change="fetchList"
                        @current-change="fetchList"
                />
            </div>
        </el-card>
    </div>
</template>

<script>
    import { getEvaluationList, deleteEvaluation } from '@/api/evaluation'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'EvaluationManage',
        data() {
            return {
                loading: false,
                searchForm: {
                    orderNo: '',
                    spotName: '',
                    rating: undefined,
                    dateRange: []
                },
                tableData: [],
                total: 0,
                currentPage: 1,
                pageSize: 10
            }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                this.loading = true
                try {
                    const params = {
                        page: this.currentPage,
                        size: this.pageSize,
                        orderNo: this.searchForm.orderNo || undefined,
                        spotName: this.searchForm.spotName || undefined,
                        rating: this.searchForm.rating !== undefined && this.searchForm.rating !== ''
                            ? this.searchForm.rating : undefined,
                        startDate: this.searchForm.dateRange?.[0] || undefined,
                        endDate: this.searchForm.dateRange?.[1] || undefined
                    }
                    const res = await getEvaluationList(params)
                    this.tableData = (res.data.list || []).slice().sort((a, b) => (b.id || 0) - (a.id || 0))
                    this.total = res.data.total || 0
                } catch (error) {
                    ElMessage.error('获取评价列表失败：' + error.message)
                    console.error(error)
                } finally {
                    this.loading = false
                }
            },
            handleSearch() {
                this.currentPage = 1
                this.fetchList()
            },
            resetSearch() {
                this.searchForm = {
                    orderNo: '',
                    spotName: '',
                    rating: undefined,
                    dateRange: []
                }
                this.currentPage = 1
                this.fetchList()
            },
            async handleDelete(row) {
                try {
                    await ElMessageBox.confirm(
                        `确定要删除订单"${row.orderNo}"的评价吗？`,
                        '删除确认',
                        {
                            confirmButtonText: '确定',
                            cancelButtonText: '取消',
                            type: 'warning'
                        }
                    )
                    await deleteEvaluation(row.id)
                    ElMessage.success('删除成功')
                    this.fetchList()
                } catch (error) {
                    if (error !== 'cancel') {
                        ElMessage.error('删除失败：' + error.message)
                    }
                }
            }
        }
    }
</script>

<style scoped>
    .evaluation-manage {
        min-height: 100vh;
    }
    .search-card {
        margin-bottom: 16px;
    }
    .table-card {
        background: #fff;
    }
    .pagination-container {
        margin-top: 16px;
        display: flex;
        justify-content: flex-end;
    }
    .el-rate {
        display: inline-block;
        height: auto;
    }
</style>