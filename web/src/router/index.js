import { createRouter, createWebHistory } from 'vue-router'

// ========== 游客端路由表 ==========
const routes = [
    { path: '/', redirect: '/home' },
    {
        path: '/home',
        component: () => import('@/views/web/Home.vue'),
        meta: { title: '首页', layout: 'web' }
    },
    {
        path: '/spot/:id',
        component: () => import('@/views/web/spot/index.vue'),
        meta: { title: '景点详情', layout: 'web' }
    },
    {
        path: '/order-confirm',
        component: () => import('@/views/web/OrderConfirm.vue'),
        meta: { title: '确认订单', layout: 'web', requiresAuth: true }
    },
    {
        path: '/pay',
        component: () => import('@/views/web/Pay.vue'),
        meta: { title: '支付', layout: 'web', requiresAuth: true }
    },
    {
        path: '/orders',
        component: () => import('@/views/web/OrderList.vue'),
        meta: { title: '我的订单', layout: 'web', requiresAuth: true }
    },
    {
        path: '/order/:id',
        component: () => import('@/views/web/OrderDetail.vue'),
        meta: { title: '订单详情', layout: 'web', requiresAuth: true }
    },
    {
        path: '/evaluation-submit',
        component: () => import('@/views/web/EvaluationSubmit.vue'),
        meta: { title: '发表评价', layout: 'web', requiresAuth: true }
    },
    {
        path: '/notices',
        component: () => import('@/views/web/NoticeList.vue'),
        meta: { title: '公告列表', layout: 'web' }
    },
    {
        path: '/notice/:id',
        component: () => import('@/views/web/NoticeDetail.vue'),
        meta: { title: '公告详情', layout: 'web' }
    },
    {
        path: '/user',
        component: () => import('@/views/web/UserCenter.vue'),
        meta: { title: '个人中心', layout: 'web', requiresAuth: true }
    },
    {
        path: '/profile',
        component: () => import('@/views/web/Profile.vue'),
        meta: { title: '个人信息', layout: 'web', requiresAuth: true }
    },
    // 共用登录页
    {
        path: '/agent',
        component: () => import('@/views/web/AgentChat.vue'),
        meta: { title: 'AI 助手', layout: 'web' }
    },
    {
        path: '/login',
        component: () => import('@/views/web/Login.vue'),
        meta: { title: '登录', layout: 'web' }
    },

    { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// ========== 路由守卫 ==========
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const role = localStorage.getItem('userRole') || ''

    // 游客端只允许游客账号：残留的管理员令牌一律清除
    if (token && role === 'admin') {
        localStorage.removeItem('token')
        localStorage.removeItem('userRole')
        next('/login')
        return
    }

    // 已登录访问登录页：直接进首页
    if (to.path === '/login' && token) {
        next('/home')
        return
    }

    // 需要登录的游客页面
    if (to.meta.requiresAuth && !token) {
        next('/login')
        return
    }

    next()
})

export default router