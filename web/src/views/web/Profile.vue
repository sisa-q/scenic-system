<template>
    <div class="profile page-container">
        <van-form @submit="onSubmit">
            <van-cell-group inset>
                <van-field
                        v-model="form.nickname"
                        name="nickname"
                        label="昵称"
                        placeholder="请输入昵称"
                        :rules="[{ required: true, message: '请输入昵称' }]"
                />
                <van-field
                        v-model="form.phone"
                        name="phone"
                        label="手机号"
                        placeholder="请输入手机号"
                        type="tel"
                        :rules="[
            { required: true, message: '请输入手机号' },
            { validator: validatePhone, message: '请输入正确的手机号' }
          ]"
                />
            </van-cell-group>

            <div style="padding: 16px">
                <van-button type="primary" block round native-type="submit" :loading="submitting">
                    {{ submitting ? '保存中...' : '保存修改' }}
                </van-button>
            </div>
        </van-form>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile } from '@/api/user'
    import { isValidPhone } from '@/utils/validator'
    import { showToast } from 'vant'

    export default {
        name: 'Profile',
        data() {
            return {
                form: { nickname: '', phone: '' },
                submitting: false
            }
        },
        mounted() {
            const info = useUserStore().userInfo
            this.form.nickname = info.nickname || ''
            this.form.phone = info.phone || ''
            console.log('当前用户信息:', info)
        },
        methods: {
            validatePhone(val) {
                return !val || isValidPhone(val)
            },
            async onSubmit() {
                console.log('提交表单:', this.form)

                if (!this.form.nickname || this.form.nickname.trim() === '') {
                    showToast('请输入昵称')
                    return
                }
                if (!this.form.phone || !isValidPhone(this.form.phone)) {
                    showToast('请输入正确的手机号')
                    return
                }

                this.submitting = true
                try {
                    await updateProfile({
                        nickname: this.form.nickname.trim(),
                        phone: this.form.phone.trim()
                    })
                    // 刷新用户信息
                    await useUserStore().getUserInfo()
                    showToast('修改成功')
                    this.$router.back()
                } catch (e) {
                    console.error('修改失败:', e)
                    showToast(e.msg || e.message || '修改失败')
                } finally {
                    this.submitting = false
                }
            }
        }
    }
</script>

<style scoped>
    .profile {
        min-height: 100vh;
        background: transparent;
        padding-top: 16px;
    }
</style>