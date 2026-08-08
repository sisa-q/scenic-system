<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">核销管理</div>
                <div class="page-subtitle">核销入园凭证与查询核销记录</div>
            </div>
        </div>
        <div class="toolbar">
            <el-input v-model="verifyCode" placeholder="请输入核销码（订单号）" style="width:240px;" clearable @keyup.enter="manualVerify" />
            <el-button type="primary" @click="manualVerify">核销</el-button>
            <el-button @click="fetchList">刷新</el-button>
            <span class="toolbar-tip">严格核对身份信息，避免误核销</span>
        </div>
        <el-table :data="tableData" style="margin-top:0;" border>
            <el-table-column prop="orderNo" label="订单号" width="240" />
            <el-table-column prop="spotName" label="景点" />
            <el-table-column prop="verifyCode" label="核销码" width="240" />
            <el-table-column prop="verifyTime" label="核销时间" width="180" />
            <el-table-column prop="operator" label="核销员" />
        </el-table>
    </div>
</template>

<script>
    import { getVerifyList, doVerify } from '@/api/verify'
    import { ElMessage } from 'element-plus'

    export default {
        name: 'VerifyManage',
        data() {
            return {
                tableData: [],
                verifyCode: ''
            }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                try {
                    const res = await getVerifyList()
                    this.tableData = res.data || []
                } catch (e) {
                    console.error('获取核销记录失败:', e)
                }
            },
            async manualVerify() {
                if (!this.verifyCode) {
                    ElMessage.warning('请输入核销码')
                    return
                }
                try {
                    await doVerify({ code: this.verifyCode })
                    ElMessage.success('核销成功')
                    this.verifyCode = ''
                    this.fetchList()
                } catch (e) {
                    console.error('核销失败:', e)
                }
            }
        }
    }
</script>