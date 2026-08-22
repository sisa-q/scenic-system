<template>
    <div class="profile">
        <div class="profile-header">
            <van-icon name="arrow-left" class="profile-back" @click="$router.back()" />
            <div class="profile-header-title">编辑资料</div>
            <div class="profile-header-right"></div>
        </div>
        <div class="profile-body">
            <van-form @submit="onSubmit">
                <div class="profile-card">
                    <div class="profile-card-title">基本信息</div>
                    <van-field v-model="form.avatar" label="头像" placeholder="头像图片 URL" />
                    <van-field v-model="form.nickname" name="nickname" label="昵称" placeholder="请输入昵称" :rules="[{ required: true, message: '请输入昵称' }]" />
                    <van-field v-model="form.phone" name="phone" label="手机号" placeholder="请输入正确的手机号" type="tel" :rules="[{ required: true, message: '请输入正确的手机号' }]" />
                    <van-field v-model="form.email" label="邮箱" placeholder="选填" />
                    <van-field name="gender" label="性别">
                        <template #input>
                            <van-radio-group v-model="form.gender" direction="horizontal">
                                <van-radio name="男">男</van-radio>
                                <van-radio name="女">女</van-radio>
                                <van-radio name="保密">保密</van-radio>
                            </van-radio-group>
                        </template>
                    </van-field>
                    <van-field v-model="form.birthday" label="生日" placeholder="选择生日" readonly is-link @click="showBirthday = true" />
                    <van-field v-model="form.signature" label="个人简介" type="textarea" rows="2" maxlength="200" show-word-limit placeholder="选填" />
                </div>
                <div class="profile-card">
                    <div class="profile-card-title">修改密码（不修改请留空）</div>
                    <van-field v-model="form.oldPassword" type="password" label="原密码" placeholder="请填写原密码" />
                    <van-field v-model="form.newPassword" type="password" label="新密码" placeholder="新密码至少 6 位" />
                    <van-field v-model="form.confirmPassword" type="password" label="确认新密码" placeholder="确认新密码" />
                </div>
                <div style="padding: 16px 0 24px;">
                    <van-button type="primary" block round native-type="submit" :loading="submitting">
                        {{ submitting ? '保存中...' : '保存修改' }}
                    </van-button>
                </div>
            </van-form>
        </div>
        <van-popup v-model:show="showBirthday" position="bottom" round>
            <van-date-picker v-model="birthdayValue" :min-date="minDate" :max-date="maxDate" title="选择生日" @confirm="onBirthdayConfirm" @cancel="showBirthday = false" />
        </van-popup>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile } from '@/api/user'
    import { showToast } from 'vant'

    export default {
        name: 'Profile',
        data() {
            return {
                form: { avatar: '', nickname: '', phone: '', email: '', gender: '保密', birthday: '', signature: '', oldPassword: '', newPassword: '', confirmPassword: '' },
                submitting: false,
                showBirthday: false,
                birthdayValue: [],
                minDate: new Date(1930, 0, 1),
                maxDate: new Date()
            }
        },
        mounted() {
            const info = useUserStore().userInfo
            this.form.avatar = info.avatar || ''
            this.form.nickname = info.nickname || ''
            this.form.phone = info.phone || ''
            this.form.email = info.email || ''
            this.form.gender = info.gender || '保密'
            this.form.birthday = info.birthday || ''
            this.form.signature = info.signature || ''
            if (info.birthday) {
                const [y, m, d] = info.birthday.split('-').map(Number)
                this.birthdayValue = [y, m, d]
            }
        },
        methods: {
            isValidPhone(v) { return /^1[3-9]\d{9}$/.test(v) },
            isValidEmail(v) { return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(v) },
            onBirthdayConfirm({ selectedValues }) {
                this.form.birthday = selectedValues.join('-')
                this.showBirthday = false
            },
            async onSubmit() {
                if (!this.form.nickname || !this.form.nickname.trim()) { showToast('请输入昵称'); return }
                if (!this.form.phone || !this.isValidPhone(this.form.phone)) { showToast('请输入正确的手机号'); return }
                if (this.form.email && !this.isValidEmail(this.form.email)) { showToast('请输入正确的邮箱'); return }
                if (this.form.newPassword || this.form.confirmPassword || this.form.oldPassword) {
                    if (this.form.newPassword.length < 6) { showToast('新密码至少 6 位'); return }
                    if (this.form.newPassword !== this.form.confirmPassword) { showToast('两次输入的密码不一致'); return }
                    if (!this.form.oldPassword) { showToast('请填写原密码'); return }
                }
                const payload = { nickname: this.form.nickname.trim(), phone: this.form.phone.trim(), avatar: this.form.avatar, email: this.form.email, gender: this.form.gender, birthday: this.form.birthday, signature: this.form.signature }
                if (this.form.newPassword) { payload.oldPassword = this.form.oldPassword; payload.newPassword = this.form.newPassword }
                this.submitting = true
                try {
                    await updateProfile(payload)
                    await useUserStore().getUserInfo()
                    showToast('修改成功')
                    this.form.oldPassword = ''
                    this.form.newPassword = ''
                    this.form.confirmPassword = ''
                } catch (e) {
                    console.error('save profile failed', e)
                    showToast(e.msg || e.message || '修改失败')
                } finally {
                    this.submitting = false
                }
            }
        }
    }
</script>

<style scoped>
    .profile { min-height: 100vh; padding-bottom: 30px; background: radial-gradient(900px 420px at 15% -5%, rgba(52,130,255,0.14), transparent 60%), radial-gradient(800px 400px at 100% 8%, rgba(150,96,255,0.10), transparent 55%), linear-gradient(160deg, #070b18 0%, #0c1730 55%, #0a1226 100%); }
    .profile-header { position: sticky; top: 0; z-index: 100; display: flex; align-items: center; padding: 0 12px; height: 50px; background: rgba(10,16,34,0.94); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); border-bottom: 1px solid rgba(120,170,255,0.16); }
    .profile-back { font-size: 20px; color: #8fa0c2; width: 36px; }
    .profile-header-title { flex: 1; text-align: center; font-size: 17px; font-weight: 700; color: #e8eefc; letter-spacing: 2px; }
    .profile-header-right { width: 36px; }
    .profile-body { padding: 14px 16px 0; max-width: 720px; margin: 0 auto; }
    .profile-card { background: rgba(16,28,56,0.72); border: 1px solid rgba(120,170,255,0.16); border-radius: 14px; margin-bottom: 14px; overflow: hidden; box-shadow: 0 12px 34px rgba(0,0,0,0.35); }
    .profile-card-title { padding: 14px 16px 4px; font-size: 15px; font-weight: 700; color: #e8eefc; letter-spacing: 1px; }
    .profile-card :deep(.van-cell) { background: transparent; color: #e8eefc; }
    .profile-card :deep(.van-cell__title), .profile-card :deep(.van-cell__label) { color: #8fa0c2; }
    .profile-card :deep(.van-field__control) { color: #e8eefc; }
    .profile-card :deep(.van-field__control::placeholder) { color: #5f7399; }
    .profile-card :deep(.van-radio__label) { color: #b8c6e0; }
    .profile-card :deep(.van-cell::after) { border-color: rgba(120,170,255,0.10); }
</style>
