<template>
    <el-container class="layout-container admin-shell">
        <el-aside width="228px" class="aside">
            <Sidebar />
        </el-aside>
        <el-container>
            <el-header height="64px" class="header">
                <Header />
            </el-header>
            <el-main class="main">
                <div class="page-content"><router-view /></div>
            </el-main>
        </el-container>
    </el-container>
</template>

<script>
    import Sidebar from './Sidebar.vue'
    import Header from './Header.vue'

    export default {
        name: 'AdminLayout',
        components: { Sidebar, Header }
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
        background: rgba(255, 255, 255, 0.92);
        border-bottom: 1px solid rgba(30, 60, 110, 0.08);
        display: flex;
        align-items: center;
        padding: 0 24px;
        backdrop-filter: blur(8px);
    }
.page-content {
        width: 100%;
        max-width: 1400px;
        margin: 0 auto;
    }

    .main {
        background: linear-gradient(160deg, #f5f7fc 0%, #edf1f8 100%);
        padding: 22px 26px;
        overflow-y: auto;
    }
</style>