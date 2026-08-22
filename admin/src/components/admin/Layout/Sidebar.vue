<template>
    <div class="admin-sidebar">
        <div class="logo">
            <div class="logo-mark">宫</div>
            <div class="logo-text">
                <div class="logo-title">故宫智慧景区</div>
                <div class="logo-sub">FORBIDDEN CITY · OPS</div>
            </div>
        </div>
        <el-menu
                :default-active="$route.path"
                router
                class="side-menu"
                background-color="transparent"
                text-color="#a8b8d0"
                active-text-color="#ffffff"
                unique-opened
        >
            <el-menu-item index="/admin/dashboard">
                <el-icon><Monitor /></el-icon>
                <span>客流大屏</span>
            </el-menu-item>
            <el-menu-item index="/admin/spot">
                <el-icon><Location /></el-icon>
                <span>景点管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/ticket">
                <el-icon><Ticket /></el-icon>
                <span>票务策略</span>
            </el-menu-item>
            <el-menu-item index="/admin/timeslot">
                <el-icon><Clock /></el-icon>
                <span>分时时段</span>
            </el-menu-item>
            <el-menu-item index="/admin/order">
                <el-icon><List /></el-icon>
                <span>订单管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/evaluation">
                <el-icon><Star /></el-icon>
                <span>评价管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/notice">
                <el-icon><Notification /></el-icon>
                <span>公告发布</span>
            </el-menu-item>
            <el-menu-item index="/admin/verify">
                <el-icon><Checked /></el-icon>
                <span>核销管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/profile">
                <el-icon><User /></el-icon>
                <span>个人中心</span>
            </el-menu-item>
        </el-menu>
    </div>
</template>

<script>
    import {
        Monitor, Location, Ticket, Clock, List,
        Star, Notification, Checked, User
    } from '@element-plus/icons-vue'

    export default {
        name: 'Sidebar',
        components: {
            Monitor, Location, Ticket, Clock, List,
        Star, Notification, Checked, User
        }
    }
</script>

<style scoped>
    .admin-sidebar {
        height: 100%;
        display: flex;
        flex-direction: column;
        background: linear-gradient(180deg, #0b1626 0%, #12233c 55%, #0d1b2a 100%);
    }
    .logo {
        height: 76px;
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 0 16px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.06);
        flex-shrink: 0;
    }
    .logo-mark {
        width: 40px;
        height: 40px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #16283f;
        font-size: 22px;
        font-weight: 800;
        font-family: 'STKaiti', 'KaiTi', 'SimSun', serif;
        background: linear-gradient(135deg, #e5c97b, #c8a24a, #9c7a2e);
        box-shadow: 0 4px 14px rgba(200, 162, 74, 0.45);
        flex-shrink: 0;
    }
    .logo-title {
        color: #fff;
        font-size: 16px;
        font-weight: 700;
        letter-spacing: 3px;
        font-family: 'STKaiti', 'KaiTi', 'SimSun', serif;
        white-space: nowrap;
    }
    .logo-sub {
        color: #6f83a3;
        font-size: 9px;
        letter-spacing: 1.5px;
        margin-top: 3px;
        white-space: nowrap;
    }
    .side-menu {
        flex: 1;
        border-right: none;
        padding: 10px 10px 16px;
        overflow-y: auto;
    }
    .side-menu .el-menu-item {
        height: 46px;
        line-height: 46px;
        border-radius: 10px;
        margin: 4px 0;
        letter-spacing: 1px;
    }
    .side-menu .el-menu-item:hover {
        background: rgba(255, 255, 255, 0.06) !important;
        color: #d6e2f5 !important;
    }
    .side-menu .el-menu-item.is-active {
        background: linear-gradient(90deg, rgba(200, 162, 74, 0.22), rgba(200, 162, 74, 0.04)) !important;
        color: #f0dcae !important;
        box-shadow: inset 3px 0 0 #c8a24a;
    }
    .side-menu .el-menu-item .el-icon {
        margin-right: 10px;
        font-size: 18px;
    }
</style>
