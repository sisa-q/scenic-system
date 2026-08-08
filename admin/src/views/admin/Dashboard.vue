<template>
    <div class="admin-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">客流大屏</div>
                <div class="page-subtitle">故宫客流三维可视化 · 实时数据场决策沙盘</div>
            </div>
        </div>
        <div class="dashboard-3d">
            <FlowScene style="width:100%;height:100%;min-height:560px;" />
        </div>
    </div>
</template>

<script>
    import FlowScene from '@/components/web/FlowScene.vue'

    export default {
        name: 'Dashboard',
        components: { FlowScene }
    }
</script>

<style scoped>
    .dashboard-3d {
        width: 100%;
        height: calc(100vh - 240px);
        min-height: 560px;
        border-radius: 14px;
        overflow: hidden;
        border: 1px solid rgba(30, 60, 110, 0.10);
        box-shadow: 0 10px 30px rgba(23, 43, 77, 0.14);
    }
</style>