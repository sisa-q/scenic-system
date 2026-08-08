<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">个人中心</div>
                <div class="page-subtitle">维护管理员账号信息</div>
            </div>
        </div>
        <el-card style="max-width: 620px;">
            <template #header>个人信息</template>
            <el-form :model="form" label-width="80px">
                <el-form-item label="昵称">
                    <el-input v-model="form.nickname" style="width:300px;" />
                </el-form-item>
                <el-form-item label="手机号">
                    <el-input v-model="form.phone" style="width:300px;" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleSave">保存修改</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile } from '@/api/user'
    import { ElMessage } from 'element-plus'

    export default {
        name: 'Profile',
        data() {
            return {
                form: { nickname: '', phone: '' }
            }
        },
        mounted() {
            const info = useUserStore().userInfo
            this.form.nickname = info.nickname || ''
            this.form.phone = info.phone || ''
        },
        methods: {
            async handleSave() {
                try {
                    await updateProfile(this.form)
                    await useUserStore().getUserInfo()
                    ElMessage.success('修改成功')
                } catch (e) {
                    // 业务错误已由 request 拦截器统一提示，这里只记录日志，避免未捕获异常
                    console.error('保存失败:', e)
                }
            }
        }
    }
</script>