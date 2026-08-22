<template>
    <div class="profile page-container">
        <van-cell-group inset title="我的余额">
            <van-cell title="当前余额" :value="'￥' + fmt(userInfo.balance)" />
        </van-cell-group>
        <van-cell-group inset>
            <van-field v-model="amount" type="number" label="金额" placeholder="请输入金额" />
        </van-cell-group>
        <div style="padding: 0 16px; margin-bottom: 12px; display: flex; gap: 12px;">
            <van-button type="primary" block round @click="handleWallet('recharge')">充值</van-button>
            <van-button type="danger" block round plain @click="handleWallet('withdraw')">取现</van-button>
        </div>

        <van-form @submit="onSubmit">
            <van-cell-group inset title="基本信息">
                <van-field v-model="form.avatar" label="头像" placeholder="头像图片 URL" />
                <van-field v-model="form.nickname" name="nickname" label="?称" placeholder="请输入昵称" :rules="[{ required: true, message: '请输入昵称' }]" />
                <van-field v-model="form.phone" name="phone" label="?机号" placeholder="请输入手机号" type="tel" :rules="[{ required: true, message: '请输入手机号' }]" />
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
            </van-cell-group>

            <van-cell-group inset title="修改密码（不修改请留空）">
                <van-field v-model="form.oldPassword" type="password" label="原密码" placeholder="请输入原密码" />
                <van-field v-model="form.newPassword" type="password" label="新密码" placeholder="至少 6 位" />
                <van-field v-model="form.confirmPassword" type="password" label="确认新密码" placeholder="再次输入新密码" />
            </van-cell-group>

            <div style="padding: 16px">
                <van-button type="primary" block round native-type="submit" :loading="submitting">
                    {{ submitting ? '保存中...' : '保存修改' }}
                </van-button>
            </div>
        </van-form>

        <van-popup v-model:show="showBirthday" position="bottom" round>
            <van-date-picker v-model="birthdayValue" :min-date="minDate" :max-date="maxDate" title="选择生日" @confirm="onBirthdayConfirm" @cancel="showBirthday = false" />
        </van-popup>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { updateProfile, walletRecharge, walletWithdraw } from '@/api/user'
    import { showToast } from 'vant'

    export default {
        name: 'Profile',
        data() {
            return {
                form: {
                    avatar: '', nickname: '', phone: '', email: '', gender: '保密', birthday: '', signature: '',
                    oldPassword: '', newPassword: '', confirmPassword: ''
                },
                amount: 100,
                submitting: false,
                showBirthday: false,
                birthdayValue: [],
                minDate: new Date(1930, 0, 1),
                maxDate: new Date()
            }
        },
        computed: {
            userInfo() {
                return useUserStore().userInfo || {}
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
            fmt(v) {
                return Number(v || 0).toFixed(2)
            },
            isValidPhone(v) {
                return /^1[3-9]\d{9}$/.test(v)
            },
            isValidEmail(v) {
                return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(v)
            },
            onBirthdayConfirm({ selectedValues }) {
                this.form.birthday = selectedValues.join('-')
                this.showBirthday = false
            },
            async handleWallet(action) {
                const amt = Number(this.amount)
                if (!amt || amt <= 0) {
                    showToast('请输入有效金额')
                    return
                }
                try {
                    const fn = action === 'recharge' ? walletRecharge : walletWithdraw
                    await fn({ amount: amt })
                    await useUserStore().getUserInfo()
                    showToast(action === 'recharge' ? '充值成功' : '取现成功')
                } catch (e) {
                    // error toast handled by interceptor
                }
            },
            async onSubmit() {
                if (!this.form.nickname || !this.form.nickname.trim()) {
                    showToast('请输入昵称')
                    return
                }
                if (!this.form.phone || !this.isValidPhone(this.form.phone)) {
                    showToast('请输入正确的手机号')
                    return
                }
                if (this.form.email && !this.isValidEmail(this.form.email)) {
                    showToast('请输入正确的邮箱')
                    return
                }
                if (this.form.newPassword || this.form.confirmPassword || this.form.oldPassword) {
                    if (this.form.newPassword.length < 6) {
                        showToast('新密码至少 6 位')
                        return
                    }
                    if (this.form.newPassword !== this.form.confirmPassword) {
                        showToast('两次输入的密码不一致')
                        return
                    }
                    if (!this.form.oldPassword) {
                        showToast('请填写原密码')
                        return
                    }
                }
                const payload = {
                    nickname: this.form.nickname.trim(),
                    phone: this.form.phone.trim(),
                    avatar: this.form.avatar,
                    email: this.form.email,
                    gender: this.form.gender,
                    birthday: this.form.birthday,
                    signature: this.form.signature
                }
                if (this.form.newPassword) {
                    payload.oldPassword = this.form.oldPassword
                    payload.newPassword = this.form.newPassword
                }
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
                    showToast(e.msg || e.message || '?改失败')
                } finally {
                    this.submitting = false
                }
            }
        }
    }
</script>
