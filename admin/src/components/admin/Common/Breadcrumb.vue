<template>
    <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="title">{{ title }}</el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script>
    export default {
        name: 'Breadcrumb',
        computed: {
            title() {
                return this.$route.meta.title || ''
            }
        }
    }
</script>