import { createRouter, createWebHistory } from 'vue-router'
import { getTokenRole } from '@/utils/auth'

// ========== 管理端路由表 ==========
const routes = [
    { path: '/', redirect: '/admin/dashboard' },
    // 独立管理端登录页
    {
        path: '/login',
        component: () => import('@/views/admin/Login.vue'),
        meta: { title: '管理员登录' }
    },

    {
        path: '/admin',
        component: () => import('@/components/admin/Layout/Index.vue'),
        redirect: '/admin/dashboard',
        meta: { layout: 'admin' },
        children: [
            {
                path: 'dashboard',
                component: () => import('@/views/admin/Dashboard.vue'),
                meta: { title: '客流大屏', icon: 'Monitor' }
            },
            {
                path: 'spot',
                component: () => import('@/views/admin/SpotManage.vue'),
                meta: { title: '景点管理', icon: 'Location' }
            },
            {
                path: 'ticket',
                component: () => import('@/views/admin/TicketPolicy.vue'),
                meta: { title: '票务策略', icon: 'Ticket' }
            },
            {
                path: 'timeslot',
                component: () => import('@/views/admin/TimeSlotManage.vue'),
                meta: { title: '分时时段', icon: 'Clock' }
            },
            {
                path: 'order',
                component: () => import('@/views/admin/OrderManage.vue'),
                meta: { title: '订单管理', icon: 'List' }
            },
            {
                path: 'evaluation',
                component: () => import('@/views/admin/Evaluation.vue'),
                meta: { title: '评价管理', icon: 'Star' }
            },
            {
                path: 'notice',
                component: () => import('@/views/admin/NoticeManage.vue'),
                meta: { title: '公告发布', icon: 'Notification' }
            },
            {
                path: 'verify',
                component: () => import('@/views/admin/VerifyManage.vue'),
                meta: { title: '核销管理', icon: 'Checked' }
            },
            {
                path: 'profile',
                component: () => import('@/views/admin/Profile.vue'),
                meta: { title: '个人中心', icon: 'User' }
            }
        ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/admin/dashboard' }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// ========== 路由守卫（仅管理员可访问管理端） ==========
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const role = localStorage.getItem('userRole') || getTokenRole() || ''

    // 管理端只允许管理员账号：残留的游客令牌一律清除
    if (token && role !== 'admin') {
        localStorage.removeItem('token')
        localStorage.removeItem('userRole')
        next('/login')
        return
    }

    // 登录页
    if (to.path === '/login') {
        if (token) {
            next('/admin/dashboard')
            return
        }
        next()
        return
    }

    if (!token) {
        next('/login')
        return
    }
    next()
})

export default router
