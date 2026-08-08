<template>
    <div class="notice-detail" v-if="notice.id">
        <h2>{{ notice.title }}</h2>
        <div class="time">{{ notice.publishTime }}</div>
        <div class="content" v-html="notice.content"></div>
    </div>
    <van-empty v-else description="公告不存在" />
</template>

<script>
    import { getNoticeDetail } from '@/api/notice'

    export default {
        name: 'NoticeDetail',
        data() {
            return { notice: {} }
        },
        mounted() {
            this.fetchDetail()
        },
        methods: {
            async fetchDetail() {
                const id = this.$route.params.id
                const res = await getNoticeDetail(id)
                this.notice = res.data || {}
            }
        }
    }
</script>

<style scoped>
    .notice-detail {
        padding: 20px 16px;
        background: #fff;
        min-height: 100vh;
    }
    .notice-detail h2 {
        font-size: 20px;
        margin-bottom: 10px;
    }
    .time {
        color: #969799;
        font-size: 13px;
        padding-bottom: 16px;
        border-bottom: 1px solid #f0f0f0;
    }
    .content {
        padding-top: 16px;
        font-size: 15px;
        line-height: 1.8;
        color: #323233;
    }
</style>