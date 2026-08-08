<template>
    <div class="notice-list">
        <van-cell
                v-for="item in list"
                :key="item.id"
                :title="item.title"
                :label="item.publishTime"
                is-link
                @click="goDetail(item.id)"
        />
        <van-empty v-if="!list.length" description="暂无公告" />
    </div>
</template>

<script>
    import { getNoticeList } from '@/api/notice'

    export default {
        name: 'NoticeList',
        data() {
            return { list: [] }
        },
        mounted() {
            this.fetchList()
        },
        methods: {
            async fetchList() {
                const res = await getNoticeList()
                this.list = res.data || []
            },
            goDetail(id) {
                this.$router.push(`/notice/${id}`)
            }
        }
    }
</script>