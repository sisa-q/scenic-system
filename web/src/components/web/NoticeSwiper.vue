<template>
    <div class="notice-swiper" v-if="list && list.length">
        <van-swipe :autoplay="3000" indicator-color="#1989fa">
            <van-swipe-item v-for="item in list" :key="item.id" @click="$emit('click', item.id)">
                <div class="notice-item">📢 {{ item.title }}</div>
            </van-swipe-item>
        </van-swipe>
    </div>
</template>

<script>
    export default {
        name: 'NoticeSwiper',
        props: {
            list: {
                type: Array,
                default: () => []
            }
        },
        emits: ['click']
    }
</script>

<style scoped>
    .notice-swiper {
        background: #fff;
        border-radius: 12px;
        padding: 10px 16px;
        margin: 12px 16px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    }
    .notice-item {
        font-size: 14px;
        color: #323233;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        cursor: pointer;
    }
</style>