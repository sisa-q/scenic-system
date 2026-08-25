<template>
    <div class="gugong-detail">
        <!-- ===== 宣传片弹窗（仅首次进入显示） ===== -->
        <div v-if="showVideoDialog" class="video-dialog-overlay" @click.self="closeDialog">
            <div class="video-dialog">
                <div class="dialog-title">🏯 故宫博物院 · 宣传片</div>
                <div class="video-preview" ref="videoPreview" @click="playFullscreen">
                    <iframe
                            ref="previewVideo"
                            :src="gugongVideoUrl"
                            scrolling="no"
                            border="0"
                            frameborder="no"
                            framespacing="0"
                            allowfullscreen="true"
                            style="width:100%; height:100%;"
                    ></iframe>
                    <div class="play-icon">▶</div>
                </div>
                <div class="dialog-actions">
                    <button class="btn-fullscreen" @click="playFullscreen">🎬 全屏播放</button>
                    <button class="btn-enter" @click="enterPage">📖 直接进入</button>
                </div>
                <div class="dialog-hint">* 全屏播放将沉浸式欣赏故宫宣传片</div>
            </div>
        </div>

        <!-- ===== 主页面 ===== -->
        <div class="main-content" v-show="!showVideoDialog">
            <!-- 顶部标题（动态背景） -->
            <div class="page-header" ref="pageHeader" :style="{ backgroundImage: 'url(' + bgImages[currentBgIndex] + ')' }">
                <div class="header-content">
                    <h1 class="spot-title">🇨🇳 中国 · 故宫博物院</h1>
                    <div class="subtitle">紫禁城 · 世界文化遗产</div>
                </div>
            </div>

            <!-- 景点停用提示 -->
            <div class="closed-banner" v-if="spotClosed">
                ⚠ 该景点暂停开放，暂不可购票
            </div>

            <!-- 合并后的导航 + 内容区域 -->
            <div class="unified-area">
                <div class="nav-bar">
                    <span
                            v-for="tab in tabs"
                            :key="tab.key"
                            class="nav-item"
                            :class="{ active: activeTab === tab.key }"
                            @click="activeTab = tab.key"
                    >
                        {{ tab.label }}
                    </span>
                </div>

                <div class="content-area">
                    <!-- 视频介绍 -->
                    <div v-show="activeTab === 'video'" class="tab-content video-tab">
                        <div class="video-container">
                            <iframe
                                    :src="gugongVideoUrl"
                                    scrolling="no"
                                    border="0"
                                    frameborder="no"
                                    framespacing="0"
                                    allowfullscreen="true"
                                    style="width:100%; height:100%;"
                            ></iframe>
                        </div>
                        <div class="video-description">
                            <p>🏮 故宫，又称紫禁城，是明清两代的皇家宫殿，位于北京中轴线的中心，是世界上现存规模最大、保存最完整的木质结构古建筑群。</p>
                            <p>🎥 本片带您领略故宫的四季变换、建筑之美与文化传承。</p>
                        </div>
                    </div>

                    <!-- 文字介绍 -->
                    <div v-show="activeTab === 'text'" class="tab-content text-tab">
                        <div class="intro-card">
                            <h3>📜 故宫简介</h3>
                            <p>{{ spot.description || '故宫博物院建立于1925年，是在明朝、清朝两代皇宫及其收藏的基础上建立起来的中国综合性博物馆，也是中国最大的古代文化艺术博物馆。' }}</p>
                            <p>🏛️ 故宫占地面积72万平方米，建筑面积约15万平方米，有大小宫殿七十多座，房屋九千余间。以太和殿、中和殿、保和殿三大殿为中心。</p>
                            <p>🎨 藏品总量达186万余件（套），涵盖古代书画、瓷器、铜器、玉器、宫廷珍宝等，是中华文明的重要载体。</p>
                        </div>
                        <div class="info-grid">
                            <div class="info-item"><span>📍 位置</span> 北京市东城区景山前街4号</div>
                            <div class="info-item"><span>🕒 开放时间</span> 08:30 - 17:00（旺季）</div>
                            <div class="info-item"><span>🎫 门票</span> 旺季 60元，淡季 40元</div>
                            <div class="info-item"><span>🏅 荣誉</span> 世界文化遗产（1987）</div>
                        </div>
                    </div>

                    <!-- 图片集 -->
                    <div v-show="activeTab === 'gallery'" class="tab-content gallery-tab">
                        <div class="gallery-grid">
                            <div v-for="(img, idx) in galleryImages" :key="idx" class="gallery-item">
                                <img :src="img" :alt="'故宫景色 ' + (idx+1)" @click="previewImage(idx)" />
                            </div>
                        </div>
                        <div v-if="previewIndex !== null" class="image-preview-overlay" @click="previewIndex = null">
                            <img :src="galleryImages[previewIndex]" alt="预览" />
                        </div>
                    </div>

                    <!-- 购票选择 -->
                    <div v-show="activeTab === 'ticket'" class="tab-content ticket-tab">
                        <div class="ticket-header">
                            <h3>🎫 购票选择</h3>
                            <p>请选择您心仪的时段，开启故宫之旅</p>
                        </div>
                        <div v-if="spotClosed" class="empty-ticket">
                            <p>该景点暂停开放，暂不可购票</p>
                        </div>
                        <div v-else-if="slots && slots.length" class="slot-list">
                            <div
                                    v-for="slot in slots"
                                    :key="slot.id"
                                    class="slot-card"
                                    :class="{ disabled: slot.status !== 1 }"
                                    @click="selectSlot(slot)"
                            >
                                <div class="slot-time">{{ formatDateTime(slot.startTime) }} - {{ formatDateTime(slot.endTime) }}</div>
                                <div class="slot-info">
                                    <span class="slot-price">¥{{ slot.price || 60 }}</span>
                                    <span class="slot-remain">余票：{{ slot.quota - slot.booked }}</span>
                                </div>
                                <div v-if="slot.status !== 1" class="slot-status closed">已关闭</div>
                            </div>
                        </div>
                        <div v-else class="empty-ticket">
                            <p>暂无可用时段，请稍后再来</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 底部导航 -->
        <TabBar />
    </div>
</template>

<script>
    import { getSpotDetail, getSlotsBySpot } from '@/api/ticket'
    import TabBar from '@/components/web/TabBar.vue'
    import { showToast } from 'vant'

    export default {
        name: 'GugongDetail',
        components: { TabBar },
        data() {
            return {
                spot: {},
                slots: [],
                showVideoDialog: false,
                activeTab: 'video',
                previewIndex: null,
                pendingSlot: null,   // AI 选时段：若时段还没加载完先缓存
                gugongVideoUrl: '//player.bilibili.com/player.html?bvid=BV1jL4y1V7AM&page=1&high_quality=1&danmaku=0',
                tabs: [
                    { key: 'video', label: '视频介绍' },
                    { key: 'text', label: '文字介绍' },
                    { key: 'gallery', label: '风景图片' },
                    { key: 'ticket', label: '购票选择' },
                ],
                galleryImages: [
                    'https://picsum.photos/seed/gugong1/600/400',
                    'https://picsum.photos/seed/gugong2/600/400',
                    'https://picsum.photos/seed/gugong3/600/400',
                    'https://picsum.photos/seed/gugong4/600/400',
                    'https://picsum.photos/seed/gugong5/600/400',
                    'https://picsum.photos/seed/gugong6/600/400',
                ],
                bgImages: [
                    require('@/assets/images/gugong/biaotilan.jpg'),
                    require('@/assets/images/gugong/biaotilan1.jpg'),
                    require('@/assets/images/gugong/biaotilan2.jpg')
                ],
                currentBgIndex: 0,
                bgTimer: null
            }
        },
        computed: {
            // 景点是否停用（status=0 则暂停开放）
            spotClosed() {
                return this.spot && this.spot.status === 0
            }
        },
        async mounted() {
            // ---------- 新增：锁定 Viewport，禁止用户缩放 ----------
            const meta = document.querySelector('meta[name=viewport]')
            if (meta) {
                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'
            } else {
                // 若不存在则新建一个
                const newMeta = document.createElement('meta')
                newMeta.name = 'viewport'
                newMeta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'
                document.head.appendChild(newMeta)
            }

            const id = this.$route.params.id
            if (!id) {
                showToast('参数错误')
                this.$router.back()
                return
            }
            await this.fetchSpot(id)
            await this.fetchSlots(id)

            // 仅从首页点击“故宫”图标进入时展示宣传片弹窗，且同一会话只展示一次（返回购票页不再反复弹）
            if (this.$route.query.v === '1' && !sessionStorage.getItem('gugong_video_shown')) {
                sessionStorage.setItem('gugong_video_shown', '1')
                this.showVideoDialog = true
            }

            // 背景轮播
            this.bgTimer = setInterval(() => {
                this.currentBgIndex = (this.currentBgIndex + 1) % this.bgImages.length
            }, 6000)

            // AI 悬浮窗调度：监听 切换tab / 选时段 事件（只加脚本，不动模板与样式）
            window.addEventListener('agent:switch-tab', this.onAgentSwitchTab)
            window.addEventListener('agent:select-slot', this.onAgentSelectSlot)
        },
        beforeDestroy() {
            if (this.bgTimer) {
                clearInterval(this.bgTimer)
                this.bgTimer = null
            }
            window.removeEventListener('agent:switch-tab', this.onAgentSwitchTab)
            window.removeEventListener('agent:select-slot', this.onAgentSelectSlot)
        },
        methods: {
            async fetchSpot(id) {
                try {
                    const res = await getSpotDetail(id)
                    this.spot = (res && res.data) || res || {}
                } catch (e) {
                    console.error('获取景点详情失败:', e)
                    showToast('获取景点信息失败')
                }
            },
            async fetchSlots(id) {
                try {
                    const res = await getSlotsBySpot(id)
                    let slotsData = []
                    if (res && res.data && Array.isArray(res.data)) {
                        slotsData = res.data
                    } else if (res && Array.isArray(res)) {
                        slotsData = res
                    }
                    this.slots = slotsData.filter(slot => slot.status === 1)
                    if (this.pendingSlot) {
                        const p = this.pendingSlot
                        this.pendingSlot = null
                        this.highlightAndGo(p.slotId, p.quantity)
                    }
                } catch (e) {
                    console.error('获取时段失败:', e)
                    this.slots = []
                }
            },
            formatDateTime(dateTimeStr) {
                if (!dateTimeStr) return ''
                const normalized = dateTimeStr.replace('T', ' ')
                const parts = normalized.split(' ')
                if (parts.length >= 2) {
                    return parts[0].slice(5) + ' ' + parts[1].slice(0, 5)
                }
                return dateTimeStr
            },
            // ===== AI 悬浮窗调度 =====
            onAgentSwitchTab(e) {
                const tab = e.detail && e.detail.tab
                if (tab) this.activeTab = tab
            },
            onAgentSelectSlot(e) {
                const d = e.detail || {}
                const slotId = parseInt(d.slotId, 10)
                if (!slotId) return
                if (!this.slots.length) {
                    this.pendingSlot = { slotId, quantity: d.quantity }
                    return
                }
                this.highlightAndGo(slotId, d.quantity)
            },
            highlightAndGo(slotId, quantity) {
                if (!this.slots.length) return
                const slot = this.slots.find(s => s.id === slotId)
                if (!slot || slot.status !== 1) return
                const idx = this.slots.findIndex(s => s.id === slotId)
                const cards = this.$el ? this.$el.querySelectorAll('.slot-card') : []
                const el = cards[idx]
                if (el) {
                    // 高亮时段卡片（JS 直接改样式，不动模板/CSS）
                    el.style.borderColor = '#ffd04b'
                    el.style.boxShadow = '0 0 0 3px rgba(255,208,75,.35), 0 4px 18px rgba(255,208,75,.25)'
                    el.style.transition = 'all .3s'
                }
                // 约 0.9 秒后自动进入下单确认页（数量预填）
                setTimeout(() => {
                    if (el) { el.style.borderColor = ''; el.style.boxShadow = '' }
                    this.selectSlot(slot, quantity)
                }, 900)
            },
            selectSlot(slot, qtyOverride) {
                if (this.spotClosed) {
                    showToast('该景点暂停开放')
                    return
                }
                if (slot.status !== 1) {
                    showToast('该时段已关闭')
                    return
                }
                const price = slot.price || 60
                this.$router.push({
                    path: '/order-confirm',
                    query: {
                        slotId: slot.id,
                        spotId: this.spot.id,
                        price: price,
                        policyName: slot.policyName || '故宫门票',
                        spotName: this.spot.name || '故宫博物院',
                        startTime: slot.startTime,
                        endTime: slot.endTime,
                        quota: slot.quota,
                        booked: slot.booked,
                        quantity: (qtyOverride && qtyOverride > 0) ? qtyOverride : 1,
                    }
                })
            },
            previewImage(idx) {
                this.previewIndex = idx
            },
            closeDialog() {
                this.showVideoDialog = false
            },
            enterPage() {
                this.showVideoDialog = false
            },
            playFullscreen() {
                // 全屏播放：让宣传片占满整个电脑屏幕（整屏全屏）
                const el = this.$refs.videoPreview || this.$refs.previewVideo
                if (!el) return
                const req = el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen
                if (req) {
                    try { req.call(el) } catch (e) { /* 浏览器可能拒绝，忽略 */ }
                }
                // 不关闭弹窗：退出全屏后回到弹窗界面继续观看/操作
            },
        },
        watch: {
            // 关闭弹窗仅隐藏，不记录“已看过”，下次进入仍会展示
            showVideoDialog() {
            }
        }
    }
</script>

<style scoped>
    /* ===== 导入马善政毛笔字体（Google Fonts） ===== */
    @import url('https://fonts.googleapis.com/css2?family=Ma+Shan+Zheng&display=swap');

    /* 全局背景 + 防止横向溢出 */
    .gugong-detail {
        background: #fcf8f0;
        min-height: 100vh;
        padding-bottom: 50px;
        font-family: 'SimSun', '宋体', serif;
        overflow-x: hidden;   /* 新增：禁止横向滚动条，确保页面不溢出 */
    }

    /* ===== 弹窗样式 ===== */
    .video-dialog-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.7);
        backdrop-filter: blur(8px);
        z-index: 1000;
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .video-dialog {
        background: #1a1a1a;
        border-radius: 24px;
        max-width: 700px;
        width: 92%;
        padding: 30px 20px 20px;
        border: 2px solid #d4a017;
        box-shadow: 0 0 60px rgba(212, 160, 23, 0.3);
    }
    .dialog-title {
        font-family: 'Ma Shan Zheng', '华文行楷', 'STXingkai', cursive;
        font-weight: 700;
        font-size: 28px;
        color: #d4a017;
        text-align: center;
        margin-bottom: 16px;
        letter-spacing: 4px;
    }
    .video-preview {
        position: relative;
        cursor: pointer;
        border-radius: 12px;
        overflow: hidden;
        background: #000;
        aspect-ratio: 16/9;
        max-width: 100%;   /* 新增 */
    }
    .video-preview iframe {
        width: 100%;
        height: 100%;
    }
    /* 全屏播放时铺满整个电脑屏幕 */
    .video-preview:fullscreen {
        width: 100vw;
        height: 100vh;
        aspect-ratio: auto;
        border-radius: 0;
    }
    .play-icon {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        font-size: 64px;
        color: #fff;
        text-shadow: 0 0 30px rgba(212, 160, 23, 0.8);
        opacity: 0.8;
        pointer-events: none;
    }
    .video-preview:hover .play-icon {
        opacity: 1;
    }
    .dialog-actions {
        display: flex;
        gap: 20px;
        justify-content: center;
        margin-top: 20px;
        flex-wrap: wrap;   /* 新增：小屏自动换行 */
    }
    .dialog-actions button {
        padding: 12px 32px;
        border: none;
        border-radius: 40px;
        font-size: 18px;
        font-weight: 600;
        cursor: pointer;
        transition: transform 0.2s, box-shadow 0.2s;
        flex: 0 1 auto;   /* 新增 */
    }
    .dialog-actions button:hover {
        transform: scale(1.02);
    }
    .btn-fullscreen {
        background: linear-gradient(135deg, #d4a017, #b8860b);
        color: #fff;
        box-shadow: 0 4px 20px rgba(212, 160, 23, 0.5);
    }
    .btn-enter {
        background: rgba(255, 255, 255, 0.15);
        color: #fff;
        border: 1px solid rgba(255, 255, 255, 0.3) !important;
    }
    .dialog-hint {
        text-align: center;
        color: #aaa;
        font-size: 13px;
        margin-top: 12px;
    }

    /* ===== 主内容 ===== */
    .main-content {
        padding: 0;
    }

    /* ===== 标题栏 ===== */
    .page-header {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 600px;
        padding: 20px 0;
        margin-bottom: 8px;
        border-bottom: 2px solid #d4a017;
        background-size: cover;
        background-position: center;
        background-repeat: no-repeat;
        position: relative;
        overflow: hidden;
    }
    .header-content {
        text-align: center;
        z-index: 1;
        max-width: 100%;
        padding: 0 15px;   /* 新增：防止标题贴边 */
    }
    .spot-title {
        font-family: 'Ma Shan Zheng', '华文行楷', 'STXingkai', cursive;
        font-weight: 700;
        font-size: 40px;
        color: #000;
        letter-spacing: 6px;
        text-shadow: 0 0 8px rgba(255,255,255,0.9), 1px 1px 3px rgba(255,255,255,0.6);
        margin: 0 0 4px;
        word-break: break-word;   /* 新增：防止长单词溢出 */
    }
    .subtitle {
        font-family: 'Ma Shan Zheng', '华文行楷', 'STXingkai', cursive;
        font-weight: 700;
        font-size: 20px;
        color: #000;
        letter-spacing: 4px;
        margin-top: 2px;
        text-shadow: 0 0 8px rgba(255,255,255,0.9), 1px 1px 3px rgba(255,255,255,0.6);
    }

    /* ===== 合并区域 ===== */
    .unified-area {
        background: url('~@/assets/images/gugong/zhanshilan.jpg') center/cover no-repeat;
        border-radius: 0;
        box-shadow: 0 2px 16px rgba(0,0,0,0.04);
        overflow: hidden;
        max-width: 100%;   /* 新增 */
    }

    /* ===== 导航栏 ===== */
    .nav-bar {
        display: flex;
        justify-content: space-around;
        padding: 8px 4px;
        flex-wrap: wrap;
        background: rgba(255, 255, 255, 0.4);
        backdrop-filter: blur(2px);
    }
    .nav-item {
        font-family: 'KaiTi', '楷体', 'STKaiti', serif;
        font-weight: 700;
        font-size: 18px;
        color: #000;
        cursor: pointer;
        padding: 8px 14px;
        border-radius: 8px;
        transition: all 0.3s;
        white-space: nowrap;
        background: rgba(255, 255, 255, 0.7);
        text-shadow: 0 0 2px rgba(255,255,255,0.8);
        margin: 2px;
        flex: 0 1 auto;   /* 新增 */
    }
    .nav-item:hover {
        background: rgba(245, 240, 232, 0.9);
    }
    .nav-item.active {
        background: #c41a1a;
        color: #fff;
        text-shadow: none;
    }

    .content-area {
        padding: 12px 12px;
        min-height: 400px;
        background: transparent;
        max-width: 100%;   /* 新增 */
        overflow-x: hidden;
    }

    .tab-content {
        animation: fadeIn 0.3s ease;
    }
    @keyframes fadeIn {
        from { opacity: 0; transform: translateY(8px); }
        to { opacity: 1; transform: translateY(0); }
    }

    /* ===== 视频容器 ===== */
    .video-container {
        border-radius: 12px;
        overflow: hidden;
        background: #000;
        aspect-ratio: 16/9;
        max-width: 100%;   /* 新增 */
    }
    .video-container iframe {
        width: 100%;
        height: 100%;
    }
    .video-description {
        margin-top: 12px;
        font-size: 17px;
        line-height: 1.8;
        color: #000;
        background: rgba(255, 255, 255, 0.7);
        padding: 10px 12px;
        border-radius: 8px;
        max-width: 100%;
        word-wrap: break-word;
    }
    .video-description p {
        font-family: 'SimSun', '宋体', serif;
        font-size: 17px;
        margin-bottom: 6px;
    }

    /* ===== 文字介绍 ===== */
    .intro-card {
        background: rgba(255, 255, 255, 0.7);
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 12px;
        max-width: 100%;
        word-wrap: break-word;
    }
    .intro-card h3 {
        font-family: 'SimSun', '宋体', serif;
        font-size: 22px;
        color: #000;
        border-left: 4px solid #c41a1a;
        padding-left: 10px;
        margin-bottom: 10px;
    }
    .intro-card p {
        font-family: 'SimSun', '宋体', serif;
        font-size: 17px;
        line-height: 1.8;
        color: #000;
        margin-bottom: 8px;
    }

    .info-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 10px;
        margin-top: 12px;
        max-width: 100%;
    }
    .info-item {
        background: rgba(255, 255, 255, 0.7);
        padding: 10px 12px;
        border-radius: 8px;
        font-size: 16px;
        color: #000;
        border-left: 3px solid #c41a1a;
        font-family: 'SimSun', '宋体', serif;
        word-break: break-word;
    }
    .info-item span {
        display: block;
        font-weight: 600;
        color: #000;
        font-size: 15px;
        margin-bottom: 2px;
    }

    /* ===== 图片集 ===== */
    .gallery-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 8px;
        max-width: 100%;
    }
    .gallery-item {
        border-radius: 8px;
        overflow: hidden;
        cursor: pointer;
        box-shadow: 0 2px 8px rgba(0,0,0,0.06);
        transition: transform 0.3s;
        aspect-ratio: 1/1;
        background: rgba(255, 255, 255, 0.5);
    }
    .gallery-item:hover {
        transform: scale(1.02);
    }
    .gallery-item img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }
    .image-preview-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.85);
        z-index: 2000;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
    }
    .image-preview-overlay img {
        max-width: 90%;
        max-height: 90%;
        border-radius: 12px;
        box-shadow: 0 0 40px rgba(0,0,0,0.6);
    }

    /* ===== 购票选择 ===== */
    .ticket-header h3 {
        font-family: 'SimSun', '宋体', serif;
        font-size: 22px;
        color: #000;
        margin-bottom: 2px;
    }
    .ticket-header p {
        font-family: 'SimSun', '宋体', serif;
        color: #000;
        font-size: 16px;
        margin-bottom: 12px;
    }
    .slot-list {
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-width: 100%;
    }
    .slot-card {
        background: rgba(255, 255, 255, 0.7);
        border-radius: 12px;
        padding: 12px 16px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-left: 4px solid #c41a1a;
        cursor: pointer;
        transition: all 0.2s;
        flex-wrap: wrap;   /* 新增：小屏换行 */
        gap: 8px;
    }
    .slot-card:hover:not(.disabled) {
        transform: translateX(4px);
        box-shadow: 0 2px 12px rgba(0,0,0,0.1);
    }
    .slot-card.disabled {
        opacity: 0.5;
        cursor: not-allowed;
        border-left-color: #ccc;
    }
    .slot-time {
        font-family: 'SimSun', '宋体', serif;
        font-weight: 600;
        font-size: 17px;
        color: #000;
        flex: 1 1 auto;
    }
    .slot-info {
        display: flex;
        gap: 16px;
        align-items: center;
        flex-wrap: wrap;
    }
    .slot-price {
        font-family: 'SimSun', '宋体', serif;
        font-size: 19px;
        font-weight: 700;
        color: #c41a1a;
    }
    .slot-remain {
        font-family: 'SimSun', '宋体', serif;
        font-size: 15px;
        color: #000;
    }
    .slot-status.closed {
        background: #e74c3c;
        color: #fff;
        padding: 2px 10px;
        border-radius: 20px;
        font-size: 13px;
    }
    .empty-ticket {
        text-align: center;
        padding: 30px 0;
        color: #000;
        background: rgba(255, 255, 255, 0.6);
        border-radius: 8px;
        font-family: 'SimSun', '宋体', serif;
        font-size: 16px;
    }

    /* ===== 响应式 ===== */
    @media (max-width: 600px) {
        .page-header {
            min-height: 250px;
        }
        .spot-title {
            font-size: 28px;
        }
        .subtitle {
            font-size: 16px;
        }
        .nav-item {
            font-size: 16px;
            padding: 4px 8px;
        }
        .info-grid {
            grid-template-columns: 1fr;
        }
        .gallery-grid {
            grid-template-columns: repeat(2, 1fr);
        }
        .slot-card {
            flex-direction: column;
            align-items: flex-start;
            gap: 8px;
        }
        .dialog-actions {
            flex-direction: column;
            align-items: center;
        }
        .dialog-actions button {
            width: 100%;
            max-width: 280px;
        }
    }

    /* 景点停用提示 */
    .closed-banner {
        margin: 14px 16px 0;
        padding: 12px 16px;
        border-radius: 12px;
        background: #fff1f0;
        border: 1px solid #ffa39e;
        color: #cf1322;
        font-size: 15px;
        font-weight: 600;
        text-align: center;
    }
</style>