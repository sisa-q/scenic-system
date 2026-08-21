<template>
    <div class="evaluation-submit page-container">
        <van-cell-group inset>
            <van-cell title="评分">
                <template #right-icon>
                    <StarRating v-model="score" />
                </template>
            </van-cell>
            <van-field
                    v-model="content"
                    type="textarea"
                    rows="4"
                    placeholder="请分享您的游览体验..."
                    maxlength="500"
                    show-word-limit
            />
        </van-cell-group>

        <div style="padding: 16px;">
            <van-button type="primary" block round @click="submit">提交评价</van-button>
        </div>
    </div>
</template>

<script>
    import { submitEvaluation } from '@/api/evaluation'
    import { showToast } from 'vant'
    import StarRating from '@/components/web/StarRating.vue'

    export default {
        name: 'EvaluationSubmit',
        components: { StarRating },
        data() {
            return {
                score: 5,
                content: '',
                orderId: ''
            }
        },
        mounted() {
            this.orderId = this.$route.query.orderId
        },
        methods: {
            async submit() {
                if (!this.content.trim()) {
                    showToast('请填写评价内容')
                    return
                }
                try {
                    await submitEvaluation({
                        orderId: this.orderId,
                        score: this.score,
                        content: this.content
                    })
                    showToast('评价成功')
                    // ✅ 跳转到订单详情页，刷新状态
                    this.$router.push(`/order/${this.orderId}`)
                } catch (e) {
                    showToast(e.msg || '提交失败')
                }
            }
        }
    }
</script>