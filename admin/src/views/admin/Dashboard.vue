<template>
    <div class="admin-page dashboard-page">
        <div class="page-header">
            <div class="page-header-left">
                <div class="page-title">客流大屏 · 三维可视化</div>
                <div class="page-subtitle">多景点切换 · 实时客流仿真 · 摄影艺术 × 科幻智能</div>
            </div>
            <div class="page-header-right">
                <el-button :type="navVisible ? 'default' : 'primary'" size="small" plain @click="navVisible = !navVisible">
                    {{ navVisible ? '收起景点导航' : '展开景点导航' }}
                </el-button>
            </div>
        </div>
        <transition name="fade">
            <div v-if="navVisible" class="spot-nav">
                <div v-for="s in spots" :key="s.id" class="spot-nav-item" :class="{ active: currentSpot && currentSpot.id === s.id, open: isOpen(s) }" @click="selectSpot(s)">
                    <span class="dot"></span>
                    <span class="name">{{ s.name }}</span>
                    <span v-if="isOpen(s)" class="badge open">已开放</span>
                    <span v-else class="badge soon">敬请期待</span>
                </div>
            </div>
        </transition>
        <div class="dashboard-3d">
            <div class="dash-scene">
                <FlowScene v-if="currentSpot && isOpen(currentSpot)" style="width:100%;height:100%;" />
                <div v-else class="coming-soon">
                    <div class="soon-glow"></div>
                    <div class="soon-icon">?</div>
                    <div class="soon-title">{{ currentSpot ? currentSpot.name : '景点' }}</div>
                    <div class="soon-text">三维可视化建设中 · 敬请期待</div>
                </div>
            </div>
            <div class="dash-metrics">
                <div class="metric-card"><div class="metric-label">今日订单</div><div class="metric-value">{{ stats.todayOrders || 0 }}</div></div>
                <div class="metric-card"><div class="metric-label">今日入园</div><div class="metric-value">{{ stats.todayEntered || 0 }}</div></div>
                <div class="metric-card"><div class="metric-label">在园客流</div><div class="metric-value">{{ realtime.currentVisitors || stats.currentVisitors || 0 }}</div></div>
                <div class="metric-card"><div class="metric-label">累计游客</div><div class="metric-value">{{ stats.totalVisitors || 0 }}</div></div>
            </div>
            <div class="dash-charts">
                <div class="chart-card"><div class="chart-title">客流趋势（近 N 天）</div><div ref="trendChart" class="chart-box"></div></div>
                <div class="chart-card"><div class="chart-title">客流时段分布</div><div ref="hourlyChart" class="chart-box"></div></div>
                <div class="chart-card"><div class="chart-title">订单状态占比</div><div ref="statusChart" class="chart-box"></div></div>
            </div>
            <div class="dash-todo">
                <div class="todo-title">待办中心</div>
                <div v-if="refundList.length" class="todo-list">
                    <div v-for="r in refundList" :key="r.id" class="todo-item" @click="$router.push('/admin/order?status=5')">
                        <span class="todo-tag">退款审核</span>
                        <span class="todo-text">{{ r.orderNo }}</span>
                        <span class="todo-go">去处理 →</span>
                    </div>
                </div>
                <div v-else class="todo-empty">暂无待办</div>
            </div>
        </div>
    </div>
</template>

<script>
    import FlowScene from '@/components/web/FlowScene.vue'
    import { getSpotList } from '@/api/spot'
    import * as echarts from 'echarts'
    import { getFlowStats, getRealTime } from '@/api/flow'
    import { getOrderList } from '@/api/order'

    export default {
        name: 'Dashboard',
        components: { FlowScene },
        data() {
            return {
                spots: [],
                currentSpot: null,
                navVisible: true,
                stats: {},
                realtime: {},
                refundList: [],
                statusCounts: [],
                chartInstances: {},
                isFullscreen: false
            }
        },
        async mounted() {
            try {
                const res = await getSpotList({})
                this.spots = (res.data || []).slice().sort((a, b) => a.id - b.id)
                this.currentSpot = this.spots.find(s => this.isOpen(s)) || this.spots[0] || null
            } catch (e) {
                // 接口异常时兜底：默认故宫
                this.spots = [{ id: 1, name: '中国-故宫', status: 1 }]
                this.currentSpot = this.spots[0]
            }
            this.loadDashboard()
        },
        methods: {
        async loadDashboard() {
            try { const s = await getFlowStats({}); this.stats = s.data || {} } catch (e) {}
            try { const r = await getRealTime(); this.realtime = r.data || {} } catch (e) {}
            try {
                const r5 = await getOrderList({ status: 5, page: 1, size: 5 })
                this.refundList = (r5.data && (r5.data.list || [])) || []
            } catch (e) {}
            this.loadStatusCounts()
            this.$nextTick(() => this.initCharts())
            window.addEventListener('resize', this.resizeCharts)
            document.addEventListener('fullscreenchange', this.fullscreenChange)
        },
        async loadStatusCounts() {
            const st = [0, 1, 2, 3, 5]
            const names = { 0: '待支付', 1: '已支付', 2: '已核销', 3: '已退款', 5: '退款中' }
            const arr = []
            for (const s of st) {
                try { const r = await getOrderList({ status: s, page: 1, size: 1 }); arr.push({ name: names[s], value: (r.data && r.data.total) || 0 }) } catch (e) { arr.push({ name: names[s], value: 0 }) }
            }
            this.statusCounts = arr
            this.$nextTick(() => this.renderStatus())
        },
        initCharts() {
            this.renderTrend()
            this.renderHourly()
        },
        renderTrend() {
            if (!this.$refs.trendChart) return
            const chart = echarts.init(this.$refs.trendChart)
            chart.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'axis' },
                grid: { left: 42, right: 16, top: 30, bottom: 24 },
                xAxis: { type: 'category', data: this.stats.dates || [], axisLine: { lineStyle: { color: '#5b6b8c' } } },
                yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(120,170,255,0.12)' } } },
                series: [{ type: 'line', smooth: true, data: this.stats.trend || [], areaStyle: { opacity: 0.25 }, lineStyle: { color: '#4da3ff' }, itemStyle: { color: '#4da3ff' } }]
            })
            this.chartInstances.trend = chart
        },
        renderHourly() {
            if (!this.$refs.hourlyChart) return
            const chart = echarts.init(this.$refs.hourlyChart)
            const hd = this.stats.hourlyDistribution || []
            chart.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'axis' },
                grid: { left: 42, right: 16, top: 30, bottom: 30 },
                xAxis: { type: 'category', data: hd.map(i => i.name || ''), axisLabel: { rotate: 40, fontSize: 10 } },
                yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(120,170,255,0.12)' } } },
                series: [{ type: 'bar', data: hd.map(i => i.value || 0), itemStyle: { color: '#7c6bff' } }]
            })
            this.chartInstances.hourly = chart
        },
        renderStatus() {
            if (!this.$refs.statusChart) return
            const chart = echarts.init(this.$refs.statusChart)
            chart.setOption({
                backgroundColor: 'transparent',
                tooltip: { trigger: 'item' },
                legend: { bottom: 0, textStyle: { color: '#aebcd8' } },
                series: [{ type: 'pie', radius: ['40%', '65%'], center: ['50%', '44%'], data: this.statusCounts, label: { color: '#cfe0ff' } }]
            })
            this.chartInstances.status = chart
        },
        toggleFullscreen() {
            const el = this.$refs.dash3d
            if (!document.fullscreenElement) {
                if (el && el.requestFullscreen) { el.requestFullscreen().catch(() => {}) }
            } else {
                if (document.exitFullscreen) document.exitFullscreen()
            }
        },
        fullscreenChange() {
            this.isFullscreen = !!document.fullscreenElement
            this.$nextTick(() => this.resizeCharts())
        },
        resizeCharts() {
            Object.keys(this.chartInstances).forEach(k => this.chartInstances[k] && this.chartInstances[k].resize())
        },
        beforeDestroy() {
            Object.keys(this.chartInstances).forEach(k => this.chartInstances[k] && this.chartInstances[k].dispose())
            window.removeEventListener('resize', this.resizeCharts)
            document.removeEventListener('fullscreenchange', this.fullscreenChange)
        },

            // 只有“故宫”开放三维场景，其余景点敬请期待
            isOpen(s) {
                return !!s && !!s.name && String(s.name).indexOf('故宫') >= 0
            },
            selectSpot(s) {
                this.currentSpot = s
            }
        }
    }
</script>

<style scoped>
    .dashboard-page {
        /* 科幻智能深空背景 */
        background:
            radial-gradient(1100px 500px at 15% 0%, rgba(50, 130, 255, 0.16), transparent 60%),
            radial-gradient(900px 460px at 100% 0%, rgba(140, 90, 255, 0.14), transparent 55%),
            linear-gradient(165deg, #070b18 0%, #0b1428 60%, #080f20 100%);
        min-height: calc(100vh - 20px);
        display: flex;
        flex-direction: column;
    }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px; }
    .page-title { font-size: 20px; font-weight: 700; letter-spacing: 2px; color: #e8eefc; }
    .page-subtitle { color: #7d8db0; font-size: 12px; margin-top: 4px; }

    /* 景点导航栏 */
    .spot-nav {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        padding: 10px 12px;
        margin-bottom: 12px;
        border-radius: 12px;
        background: rgba(16, 28, 56, 0.6);
        border: 1px solid rgba(120, 170, 255, 0.14);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        max-height: 132px;
        overflow-y: auto;
    }
    .spot-nav-item {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        border-radius: 999px;
        cursor: pointer;
        font-size: 12px;
        color: #aebcd8;
        border: 1px solid transparent;
        transition: all .15s;
        user-select: none;
    }
    .spot-nav-item:hover { background: rgba(77, 163, 255, 0.12); }
    .spot-nav-item.active {
        color: #fff;
        background: linear-gradient(135deg, rgba(77,163,255,0.35), rgba(140,90,255,0.28));
        border-color: rgba(120,180,255,0.45);
        box-shadow: 0 0 14px rgba(77,163,255,0.3);
    }
    .spot-nav-item .dot { width: 6px; height: 6px; border-radius: 50%; background: #44567a; }
    .spot-nav-item.open .dot { background: #35d18b; box-shadow: 0 0 8px #35d18b; }
    .badge { font-size: 10px; padding: 1px 6px; border-radius: 999px; }
    .badge.open { color: #35d18b; background: rgba(53,209,139,0.12); }
    .badge.soon { color: #8fa0c2; background: rgba(143,160,194,0.12); }

    /* 大屏容器 */
    .dash-scene { flex: 1; min-height: 300px; border-radius: 10px; overflow: hidden; position: relative; background: #0a0a2a; }
    .dashboard-3d {
        position: relative;
        width: 100%;
        height: auto;
        flex: 1;
        min-height: 460px;
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 12px;
        border-radius: 14px;
        overflow: hidden;
        border: 1px solid rgba(120, 170, 255, 0.16);
        box-shadow: 0 14px 40px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255,255,255,0.05);
        background: #0a0a2a;
    }

    /* 敬请期待 */
    .coming-soon {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 10px;
        color: #cfe0ff;
        background:
            radial-gradient(700px 380px at 50% 30%, rgba(60, 130, 255, 0.12), transparent 65%),
            #0a0f24;
    }
    .soon-glow { position: absolute; width: 320px; height: 320px; border-radius: 50%; background: radial-gradient(circle, rgba(90,160,255,0.18), transparent 70%); filter: blur(10px); }
    .soon-icon { font-size: 54px; color: #4da3ff; text-shadow: 0 0 30px rgba(77,163,255,0.8); }
    .soon-title { font-size: 26px; font-weight: 800; letter-spacing: 4px; }
    .soon-text { font-size: 14px; color: #7d8db0; letter-spacing: 2px; }

    .fade-enter-active, .fade-leave-active { transition: opacity .2s, transform .2s; }
    .fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-6px); }

    /* ==== 驾驶舱样式 ==== */
    .dash-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; flex-shrink: 0; }
    .metric-card { background: rgba(16, 28, 56, 0.6); border: 1px solid rgba(120,170,255,0.14); border-radius: 12px; padding: 14px 16px; backdrop-filter: blur(8px); }
    .metric-label { font-size: 12px; color: #7d8db0; }
    .metric-value { font-size: 26px; font-weight: 800; color: #6ea8ff; margin-top: 6px; }
    .dash-charts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; flex-shrink: 0; }
    .chart-card { background: rgba(16, 28, 56, 0.6); border: 1px solid rgba(120,170,255,0.14); border-radius: 12px; padding: 12px; }
    .chart-title { font-size: 13px; color: #aebcd8; margin-bottom: 8px; }
    .chart-box { flex: 1; width: 100%; height: auto; min-height: 90px; }
    .dash-todo { background: rgba(16, 28, 56, 0.6); border: 1px solid rgba(120,170,255,0.14); border-radius: 12px; padding: 8px 14px; margin-bottom: 12px; flex-shrink: 0; }
    .todo-title { font-size: 14px; color: #e8eefc; font-weight: 700; margin-bottom: 8px; }
    .todo-list { display: flex; flex-direction: column; gap: 6px; }
    .todo-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; border-radius: 8px; background: rgba(255,255,255,0.04); cursor: pointer; transition: background .15s; }
    .todo-item:hover { background: rgba(77,163,255,0.14); }
    .todo-tag { font-size: 11px; color: #ffd27a; background: rgba(255,210,122,0.12); padding: 2px 8px; border-radius: 999px; }
    .todo-text { flex: 1; font-size: 13px; color: #cfe0ff; font-family: monospace; }
    .todo-go { font-size: 12px; color: #4da3ff; }
    .todo-empty { color: #7d8db0; font-size: 13px; padding: 10px 0; }
    @media (max-width: 1100px) { .dash-charts { grid-template-columns: 1fr; } .dash-metrics { grid-template-columns: repeat(2, 1fr); } }
    .dashboard-3d:fullscreen { width: 100vw; height: 100vh; border-radius: 0; }
    .dashboard-3d:fullscreen .dash-side { width: 430px; }
    .dashboard-3d:fullscreen .chart-box { height: 150px; }

    /* ==== 数据模块悬浮在 3D 场景内 ==== */
    .dashboard-3d { position: relative; }
    .dashboard-3d > .dash-metrics, .dashboard-3d > .dash-charts, .dashboard-3d > .dash-todo { position: absolute; z-index: 20; }
    .dash-metrics { top: 10px; left: 10px; right: 10px; }
    .dash-charts { left: 10px; top: 76px; bottom: 10px; width: 268px; display: flex; flex-direction: column; gap: 10px; }
    .dash-todo { right: 10px; top: 76px; bottom: 10px; width: 236px; overflow-y: auto; }
    .metric-card { padding: 8px 12px; }
    .metric-value { font-size: 20px; }
    .chart-card { flex: 1; display: flex; flex-direction: column; min-height: 0; }

    /* ==== 大屏 Grid 分区：不重叠、同一平面 ==== */
    .dashboard-3d {
        position: relative;
        width: 100%;
        flex: 1;
        min-height: 460px;
        display: grid;
        grid-template-columns: 280px 1fr 240px;
        grid-template-rows: auto 1fr;
        gap: 10px;
        padding: 10px;
        border-radius: 14px;
        border: 1px solid rgba(120, 170, 255, 0.16);
        background: #0a0a2a;
        overflow: hidden;
    }
    .dash-metrics { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
    .dash-charts { grid-column: 1; grid-row: 2; display: flex; flex-direction: column; gap: 10px; overflow: hidden; }
    .dash-scene { grid-column: 2; grid-row: 2; position: relative; border-radius: 10px; overflow: hidden; background: #0a0a2a; padding-top: 50px; }
    .dash-todo { grid-column: 3; grid-row: 2; max-height: 340px; overflow-y: auto; background: rgba(16, 28, 56, 0.5); border: 1px solid rgba(120,170,255,0.14); border-radius: 10px; padding: 8px 10px; }
    .chart-card { flex: 1; display: flex; flex-direction: column; min-height: 0; }
    .chart-box { flex: 1; width: 100%; min-height: 80px; }
    .metric-card { padding: 8px 12px; background: rgba(16, 28, 56, 0.6); border: 1px solid rgba(120,170,255,0.14); border-radius: 10px; }
    .metric-value { font-size: 20px; }
</style>