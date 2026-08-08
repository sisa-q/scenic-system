<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">分时时段</div>
                <div class="page-subtitle">管理分时预约时段与库存配额</div>
            </div>
            <div class="page-header-right">
                <el-button type="primary" @click="handleAdd">新增时段</el-button>
            </div>
        </div>

        <!-- 数据表格 -->
        <el-table :data="tableData" style="margin-top: 0" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="policyName" label="所属票种" />
            <el-table-column prop="startTime" label="开始时间" width="180" />
            <el-table-column prop="endTime" label="结束时间" width="180" />
            <el-table-column prop="quota" label="库存" />
            <el-table-column prop="booked" label="已预约" />
            <el-table-column label="状态">
                <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                        {{ row.status === 1 ? '开放' : '关闭' }}
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

        <!-- 弹窗 -->
        <el-dialog
                v-model="dialogVisible"
                :title="dialogTitle"
                width="620px"
                :close-on-click-modal="false"
                @closed="handleDialogClosed"
        >
            <el-form :model="form" label-width="100px" ref="formRef">
                <!-- 所属票种 -->
                <el-form-item label="所属票种" required>
                    <el-select v-model="form.policyId" placeholder="请选择票种" style="width: 100%">
                        <el-option v-for="item in policyOptions" :key="item.id" :label="item.name" :value="item.id" />
                    </el-select>
                </el-form-item>

                <!-- 开始时间 -->
                <el-form-item label="开始时间" required>
                    <div style="display: flex; gap: 6px; flex-wrap: wrap; align-items: center">
                        <el-input-number v-model="form.startYear" :min="2020" :max="2030" controls-position="right" style="width: 100px" placeholder="年" />
                        <span style="color: #999">年</span>
                        <el-input-number v-model="form.startMonth" :min="1" :max="12" controls-position="right" style="width: 70px" placeholder="月" />
                        <span style="color: #999">月</span>
                        <el-input-number v-model="form.startDay" :min="1" :max="31" controls-position="right" style="width: 70px" placeholder="日" />
                        <span style="color: #999">日</span>
                        <el-input-number v-model="form.startHour" :min="0" :max="23" controls-position="right" style="width: 70px" placeholder="时" />
                        <span style="color: #999">时</span>
                        <el-input-number v-model="form.startMinute" :min="0" :max="59" controls-position="right" style="width: 70px" placeholder="分" />
                        <span style="color: #999">分</span>
                    </div>
                </el-form-item>

                <!-- 结束时间 -->
                <el-form-item label="结束时间" required>
                    <div style="display: flex; gap: 6px; flex-wrap: wrap; align-items: center">
                        <el-input-number v-model="form.endYear" :min="2020" :max="2030" controls-position="right" style="width: 100px" placeholder="年" />
                        <span style="color: #999">年</span>
                        <el-input-number v-model="form.endMonth" :min="1" :max="12" controls-position="right" style="width: 70px" placeholder="月" />
                        <span style="color: #999">月</span>
                        <el-input-number v-model="form.endDay" :min="1" :max="31" controls-position="right" style="width: 70px" placeholder="日" />
                        <span style="color: #999">日</span>
                        <el-input-number v-model="form.endHour" :min="0" :max="23" controls-position="right" style="width: 70px" placeholder="时" />
                        <span style="color: #999">时</span>
                        <el-input-number v-model="form.endMinute" :min="0" :max="59" controls-position="right" style="width: 70px" placeholder="分" />
                        <span style="color: #999">分</span>
                    </div>
                </el-form-item>

                <!-- 库存 -->
                <el-form-item label="库存" required>
                    <el-input-number v-model="form.quota" :min="1" :max="99999" style="width: 180px" />
                </el-form-item>

                <!-- 状态 -->
                <el-form-item label="状态">
                    <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="开放" inactive-text="关闭" />
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
    import { getSlots, saveSlot, updateSlot, deleteSlot, getTicketList } from '@/api/ticket'
    import { ElMessage, ElMessageBox } from 'element-plus'

    export default {
        name: 'TimeSlotManage',
        data() {
            return {
                tableData: [],
                policyOptions: [],
                dialogVisible: false,
                saving: false,
                form: this.emptyForm()
            }
        },
        computed: {
            dialogTitle() {
                return this.form.id ? '编辑时段' : '新增时段'
            }
        },
        mounted() {
            this.fetchList()
            this.fetchPolicies()
        },
        methods: {
            emptyForm() {
                const now = new Date()
                return {
                    id: null,
                    policyId: '',
                    startYear: now.getFullYear(),
                    startMonth: now.getMonth() + 1,
                    startDay: now.getDate(),
                    startHour: 8,
                    startMinute: 0,
                    endYear: now.getFullYear(),
                    endMonth: now.getMonth() + 1,
                    endDay: now.getDate(),
                    endHour: 18,
                    endMinute: 0,
                    quota: 100,
                    status: 1
                }
            },
            async fetchList() {
                try {
                    const res = await getSlots({})
                    this.tableData = res.data || []
                } catch (e) {
                    console.error('获取时段失败:', e)
                }
            },
            async fetchPolicies() {
                try {
                    const res = await getTicketList()
                    this.policyOptions = res.data || []
                } catch (e) {
                    console.error('获取票种失败:', e)
                }
            },
            handleAdd() {
                this.form = this.emptyForm()
                this.dialogVisible = true
            },
            handleEdit(row) {
                const startFields = this.parseTimeToFields(row.startTime)
                const endFields = this.parseTimeToFields(row.endTime)
                this.form = {
                    id: row.id,
                    policyId: row.policyId,
                    startYear: startFields.year,
                    startMonth: startFields.month,
                    startDay: startFields.day,
                    startHour: startFields.hour,
                    startMinute: startFields.minute,
                    endYear: endFields.year,
                    endMonth: endFields.month,
                    endDay: endFields.day,
                    endHour: endFields.hour,
                    endMinute: endFields.minute,
                    quota: row.quota,
                    status: row.status
                }
                this.dialogVisible = true
            },
            handleDialogClosed() {
                this.resetForm()
            },
            resetForm() {
                this.form = this.emptyForm()
                this.$refs.formRef?.clearValidate()
            },
            parseTimeToFields(timeStr) {
                // 支持 'YYYY-MM-DD HH:mm:ss' 或 'YYYY-MM-DD HH:mm'
                const m = String(timeStr || '').match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})/)
                if (!m) {
                    const now = new Date()
                    return { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate(), hour: 8, minute: 0 }
                }
                return {
                    year: parseInt(m[1], 10),
                    month: parseInt(m[2], 10),
                    day: parseInt(m[3], 10),
                    hour: parseInt(m[4], 10),
                    minute: parseInt(m[5], 10)
                }
            },
            formatTimeFromFields(f) {
                const pad = (n) => (n < 10 ? '0' + n : '' + n)
                return f.year + '-' + pad(f.month) + '-' + pad(f.day) + ' ' + pad(f.hour) + ':' + pad(f.minute) + ':00'
            },
            async handleSave() {
                if (!this.form.policyId) {
                    ElMessage.warning('请选择所属票种')
                    return
                }
                if (!this.form.startYear || !this.form.startMonth || !this.form.startDay ||
                    this.form.startHour === undefined || this.form.startMinute === undefined) {
                    ElMessage.warning('请完整填写开始时间')
                    return
                }
                if (!this.form.endYear || !this.form.endMonth || !this.form.endDay ||
                    this.form.endHour === undefined || this.form.endMinute === undefined) {
                    ElMessage.warning('请完整填写结束时间')
                    return
                }
                const startTime = this.formatTimeFromFields({
                    year: this.form.startYear, month: this.form.startMonth, day: this.form.startDay,
                    hour: this.form.startHour, minute: this.form.startMinute
                })
                const endTime = this.formatTimeFromFields({
                    year: this.form.endYear, month: this.form.endMonth, day: this.form.endDay,
                    hour: this.form.endHour, minute: this.form.endMinute
                })
                if (!this.form.quota || this.form.quota < 1) {
                    ElMessage.warning('库存至少为 1')
                    return
                }
                if (startTime >= endTime) {
                    ElMessage.warning('结束时间必须晚于开始时间')
                    return
                }
                const submitData = {
                    id: this.form.id,
                    policyId: this.form.policyId,
                    startTime: startTime,
                    endTime: endTime,
                    quota: this.form.quota,
                    status: this.form.status
                }
                this.saving = true
                try {
                    if (this.form.id) {
                        await updateSlot(submitData)
                        ElMessage.success('时段更新成功')
                    } else {
                        await saveSlot(submitData)
                        ElMessage.success('时段新增成功')
                    }
                    this.dialogVisible = false
                    await this.fetchList()
                } catch (e) {
                    // 业务错误已由 request 拦截器统一提示，这里只记录日志，避免重复提示或显示 Object
                    console.error('保存失败:', e)
                } finally {
                    this.saving = false
                }
            },
            handleDelete(id) {
                ElMessageBox.confirm('确认删除该时段？', '提示', { type: 'warning' })
                    .then(async () => {
                        await deleteSlot(id)
                        ElMessage.success('删除成功')
                        await this.fetchList()
                    })
                    .catch(() => {})
            }
        }
    }
</script>