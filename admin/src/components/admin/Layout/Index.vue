<template>
    <el-container class="layout-container admin-shell">
        <el-aside width="228px" class="aside">
            <Sidebar />
        </el-aside>
        <el-container>
            <el-main class="main">
                <div class="page-content"><router-view /></div>
            </el-main>
        </el-container>
    </el-container>
</template>

<script>
    import Sidebar from './Sidebar.vue'

    export default {
        name: 'AdminLayout',
        components: { Sidebar }
    }
</script>

<style scoped>
    .layout-container {
        height: 100vh;
    }
    .aside {
        background: linear-gradient(180deg, #0b1626 0%, #12233c 55%, #0d1b2a 100%);
        overflow-y: hidden;
        flex-shrink: 0;
        box-shadow: 2px 0 18px rgba(13, 27, 42, 0.16);
    }
    .header {
        background: rgba(13, 27, 42, 0.85);
        border-bottom: 1px solid rgba(30, 60, 110, 0.08);
        display: flex;
        align-items: center;
        padding: 0 24px;
        backdrop-filter: blur(8px);
    }
.page-content {
        width: 100%;
    }

    .main {
        background: radial-gradient(1100px 500px at 15% 0%, rgba(50, 130, 255, 0.12), transparent 60%), linear-gradient(165deg, #0b1428 0%, #0d1b2a 100%);
        color: #e8eefc;
        padding: 10px 16px;
        overflow-y: auto;
    }
</style>