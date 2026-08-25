<template>
    <van-tabbar v-model="active" route>
        <van-tabbar-item icon="home-o" to="/home">首页</van-tabbar-item>
        <van-tabbar-item icon="orders-o" to="/orders">订单</van-tabbar-item>
        <van-tabbar-item icon="chat-o" @click="openAgent">AI 助手</van-tabbar-item>
        <van-tabbar-item icon="user-o" to="/user">我的</van-tabbar-item>
    </van-tabbar>
</template>

<script>
    import { useAgentStore } from '@/store/modules/agent'

    export default {
        name: 'TabBar',
        setup() {
            const agentStore = useAgentStore()
            return { agentStore }
        },
        data() {
            return {
                active: 0
            }
        },
        methods: {
            openAgent() {
                this.agentStore.open()
            }
        }
    }
</script>