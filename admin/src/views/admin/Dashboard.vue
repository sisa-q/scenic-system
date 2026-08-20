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

        <!-- ===== 可隐藏的多景点导航栏 ===== -->
        <transition name="fade">
            <div v-if="navVisible" class="spot-nav">
                <div
                    v-for="s in spots"
                    :key="s.id"
                    class="spot-nav-item"
                    :class="{ active: currentSpot && currentSpot.id === s.id, open: isOpen(s) }"
                    @click="selectSpot(s)"
                >
                    <span class="dot"></span>
                    <span class="name">{{ s.name }}</span>
                    <span v-if="isOpen(s)" class="badge open">已开放</span>
                    <span v-else class="badge soon">敬请期待</span>
                </div>
            </div>
        </transition>

        <!-- ===== 大屏主体 ===== -->
        <div class="dashboard-3d">
            <!-- 故宫：渲染三维客流大屏 -->
            <FlowScene
                    v-if="currentSpot && isOpen(currentSpot)"
                    style="width:100%;height:100%;min-height:560px;"
            />
            <!-- 其它景点：敬请期待 -->
            <div v-else class="coming-soon">
                <div class="soon-glow"></div>
                <div class="soon-icon">✦</div>
                <div class="soon-title">{{ currentSpot ? currentSpot.name : '景点' }}</div>
                <div class="soon-text">三维场景建设中 · 敬请期待</div>
            </div>
        </div>
    </div>
</template>

<script>
    import FlowScene from '@/components/web/FlowScene.vue'
    import { getSpotList } from '@/api/spot'

    export default {
        name: 'Dashboard',
        components: { FlowScene },
        data() {
            return {
                spots: [],
                currentSpot: null,
                navVisible: true
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
        },
        methods: {
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
        min-height: 100vh;
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
    .dashboard-3d {
        position: relative;
        width: 100%;
        height: calc(100vh - 260px);
        min-height: 560px;
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
</style>
