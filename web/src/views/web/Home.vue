<template>
    <div class="home-container">
        <div ref="sceneContainer" class="scene-container"></div>

        <!-- HUD -->
        <div class="hud-overlay">
            <!-- 顶部公告栏 -->
            <div class="notice-bar" v-if="notices.length" @click="goNotice(currentNotice.id)">
                <span class="notice-bar-tag">📢 公告</span>
                <transition name="notice-fade" mode="out-in">
                    <span :key="noticeIndex" class="notice-bar-text">{{ currentNotice.title }}</span>
                </transition>
                <span class="notice-bar-more">查看详情 ›</span>
            </div>

            <div class="hud-top-left">
                <div class="hud-title">✦ 全息导览系统</div>
                <div class="hud-subtitle">HOLOGRAPHIC NAVIGATION v3.0</div>
                <div class="search-box">
                    <input
                            type="text"
                            v-model="searchQuery"
                            placeholder="搜索国家或景点..."
                            @keyup.enter="performSearch"
                    />
                    <button @click="performSearch">🔍</button>
                    <button v-if="searchQuery" @click="clearSearch" class="clear-btn">✕</button>
                </div>
            </div>
            <div class="hud-top-right">
                <div class="hud-stats">
                    <span class="stat-label">全球景点</span>
                    <span class="stat-value">{{ hotspots.length }}</span>
                </div>
                <div class="hud-stats" style="margin-top:4px;">
                    <span class="stat-label">当前选中</span>
                    <span class="stat-value highlight">{{ selectedName || '无' }}</span>
                </div>
            </div>
            <div class="hud-bottom">
                <div class="hud-control-hint">
                    <span class="key">⟳ 左右拖动</span>
                    <span class="key">⇱ 上下俯仰</span>
                    <span class="key">◉ 点击国家热点跳转</span>
                </div>
            </div>
        </div>

        <!-- 天气提醒条（WebSocket 推送） -->
        <div class="weather-alert-bar" v-if="weatherAlerts.length">
            <span class="weather-alert-tag">🌦 天气提醒</span>
            <span class="weather-alert-text">{{ weatherAlerts[weatherAlertIndex % weatherAlerts.length] }}</span>
        </div>

        <!-- 天气面板 -->
        <div class="weather-panel-mask" v-if="weatherPanel" @click.self="closeWeatherPanel">
            <div class="weather-panel">
                <button class="weather-panel-close" @click="closeWeatherPanel">✕</button>
                <div class="weather-panel-title">{{ weatherPanel.name }} <span class="weather-panel-country">{{ weatherPanel.country }}</span></div>
                <div v-if="weatherPanel.loading" class="weather-panel-loading">天气加载中…</div>
                <template v-else-if="weatherPanel.weather.temp">
                    <div class="weather-panel-main">
                        <span class="weather-panel-icon">{{ weatherPanel.weather.icon }}</span>
                        <span class="weather-panel-temp">{{ Math.round(Number(weatherPanel.weather.temp)) }}°C</span>
                        <span class="weather-panel-text">{{ weatherPanel.weather.text }}</span>
                    </div>
                    <div class="weather-panel-detail">
                        <span>体感 {{ weatherPanel.weather.feelsLike }}°C</span>
                        <span>湿度 {{ weatherPanel.weather.humidity }}</span>
                        <span>风 {{ weatherPanel.weather.wind }}</span>
                    </div>
                    <div class="weather-panel-advice">
                        <div>舒适度：{{ weatherPanel.weather.comfort }}</div>
                        <div>穿衣：{{ weatherPanel.weather.clothing }}</div>
                        <div v-if="weatherPanel.weather.rainProb != null">降雨概率：{{ weatherPanel.weather.rainProb }}%</div>
                        <div v-if="weatherPanel.weather.alert" class="weather-panel-alert">⚠️ {{ weatherPanel.weather.alert }}</div>
                    </div>
                    <div class="weather-panel-forecast">
                        <div v-for="f in weatherPanel.weather.forecast" :key="f.date" class="forecast-item">
                            <span class="forecast-date">{{ (f.date || '').slice(5) }}</span>
                            <span class="forecast-icon">{{ f.icon }}</span>
                            <span class="forecast-text">{{ f.text }}</span>
                            <span class="forecast-temp">{{ f.tempMin }}~{{ f.tempMax }}°C</span>
                        </div>
                    </div>
                    <button class="weather-panel-btn" @click="goSpotDetail">查看景点详情 →</button>
                </template>
                <div v-else class="weather-panel-loading">暂无天气数据</div>
            </div>
        </div>

        <!-- 全球天气一览 -->
        <div class="weather-ticker" v-if="weatherTicker.length">
            <div class="weather-ticker-title">🌍 全球天气</div>
            <div class="weather-ticker-scroll">
                <div v-for="w in weatherTicker" :key="w.spotId" class="weather-ticker-item" @click="openWeatherFromTicker(w.spotId)">
                    <span class="ticker-icon">{{ w.icon }}</span>
                    <span class="ticker-name">{{ w.name }}</span>
                    <span class="ticker-temp">{{ Math.round(Number(w.temp)) }}°C</span>
                </div>
            </div>
        </div>

        <SearchPortal :value="searchQuery" @search="onPortalSearch" @cat="onPortalCat" @go-spots="onGoSpots" />
        <TabBar />
    </div>
</template>

<script>
    import * as THREE from 'three'
    import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
    import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
    import { getSpotList } from '@/api/ticket'
    import { getNoticeList } from '@/api/notice'
    import { getWeatherBatch, getWeatherNow } from '@/api/weather'
    import TabBar from '@/components/web/TabBar.vue'
    import SearchPortal from '@/components/web/SearchPortal.vue'

    export default {
        name: 'Home',
        components: { TabBar, SearchPortal },
        data() {
            return {
                spots: [],
                hotspots: [],
                selectedName: '',
                searchQuery: '',
                hoveredCard: null,
                notices: [],
                noticeIndex: 0,
                noticeTimer: null,
                weatherMap: {},
                weatherPanel: null,
                weatherAlerts: [],
                weatherAlertIndex: 0,
                weatherAlertTimer: null,
                wsWeather: null,
                weatherLabels: [],
            }
        },
        computed: {
            currentNotice() {
                if (!this.notices.length) return {}
                return this.notices[this.noticeIndex % this.notices.length] || {}
            },
            weatherTicker() {
                return Object.values(this.weatherMap)
                    .filter(w => w && w.temp != null)
                    .sort((a, b) => (a.spotId || 0) - (b.spotId || 0))
            }
        },
        async mounted() {
            await this.fetchSpots()
            this.fetchNotices()
            this.startNoticeRotation()
            this.initScene()
            this.createBackground()
            this.createParticles()
            this.createHotspots()
            this.loadWeather()
            this.initWeatherPush()
            this.animate()
            window.addEventListener('resize', this.onResize)
        },
        beforeUnmount() {
            window.removeEventListener('resize', this.onResize)
            if (this.noticeTimer) {
                clearInterval(this.noticeTimer)
                this.noticeTimer = null
            }
            if (this.wsWeather) {
                try { this.wsWeather.close() } catch (e) { }
                this.wsWeather = null
            }
            if (this.weatherAlertTimer) {
                clearInterval(this.weatherAlertTimer)
                this.weatherAlertTimer = null
            }
            cancelAnimationFrame(this.animationId)

            if (this.scene) {
                this.scene.traverse((object) => {
                    if (object.geometry) object.geometry.dispose()
                    if (object.material) {
                        if (Array.isArray(object.material)) {
                            object.material.forEach((m) => {
                                if (m.map) m.map.dispose()
                                m.dispose()
                            })
                        } else {
                            if (object.material.map) object.material.map.dispose()
                            object.material.dispose()
                        }
                    }
                })
                this.scene.clear()
            }

            if (this.renderer) {
                this.renderer.dispose()
                this.renderer.domElement.remove()
            }
            if (this.labelRenderer) {
                this.labelRenderer.domElement.remove()
            }
            if (this._cleanupInteraction) {
                this._cleanupInteraction()
            }
        },
        methods: {
            // ====== 顶部公告栏 ======
            async fetchNotices() {
                try {
                    const res = await getNoticeList()
                    const all = (res && res.data) ? res.data : []
                    // 仅展示已发布公告
                    this.notices = all.filter(n => n.status === 1 || n.status === undefined || n.status === null)
                } catch (e) {
                    console.error('获取公告失败:', e)
                    this.notices = []
                }
            },
            startNoticeRotation() {
                if (this.noticeTimer) clearInterval(this.noticeTimer)
                this.noticeTimer = setInterval(() => {
                    if (this.notices.length > 1) {
                        this.noticeIndex = (this.noticeIndex + 1) % this.notices.length
                    }
                }, 4000)
            },
            goNotice(id) {
                if (!id) return
                this.$router.push(`/notice/${id}`)
            },

            async fetchSpots() {
                try {
                    const res = await getSpotList()
                    this.spots = (res && res.data) ? res.data : []
                } catch (e) {
                    console.error('获取景点列表失败', e)
                    this.spots = []
                } finally {
                    this.hotspots = this.buildHotspots()
                }
            },

            buildHotspots() {
                return [
                    { name: '故宫', country: '中国', lat: 39.9163, lng: 116.3972, spotId: 1, flag: 'https://flagcdn.com/w320/cn.png' },
                    { name: '埃菲尔铁塔', country: '法国', lat: 48.8584, lng: 2.2945, spotId: 2, flag: 'https://flagcdn.com/w320/fr.png' },
                    { name: '大本钟', country: '英国', lat: 51.5007, lng: -0.1246, spotId: 3, flag: 'https://flagcdn.com/w320/gb.png' },
                    { name: '自由女神像', country: '美国', lat: 40.6892, lng: -74.0445, spotId: 4, flag: 'https://flagcdn.com/w320/us.png' },
                    { name: '悉尼歌剧院', country: '澳大利亚', lat: -33.8568, lng: 151.2153, spotId: 5, flag: 'https://flagcdn.com/w320/au.png' },
                    { name: '金字塔', country: '埃及', lat: 29.9792, lng: 31.1342, spotId: 6, flag: 'https://flagcdn.com/w320/eg.png' },
                    { name: '罗马斗兽场', country: '意大利', lat: 41.8902, lng: 12.4922, spotId: 7, flag: 'https://flagcdn.com/w320/it.png' },
                    { name: '富士山', country: '日本', lat: 35.3606, lng: 138.7274, spotId: 8, flag: 'https://flagcdn.com/w320/jp.png' },
                    { name: '泰姬陵', country: '印度', lat: 27.1751, lng: 78.0421, spotId: 9, flag: 'https://flagcdn.com/w320/in.png' },
                    { name: '吴哥窟', country: '柬埔寨', lat: 13.4125, lng: 103.8670, spotId: 10, flag: 'https://flagcdn.com/w320/kh.png' },
                    { name: '蓝色清真寺', country: '土耳其', lat: 41.0053, lng: 28.9769, spotId: 11, flag: 'https://flagcdn.com/w320/tr.png' },
                    { name: '莫斯科红场', country: '俄罗斯', lat: 55.7539, lng: 37.6208, spotId: 12, flag: 'https://flagcdn.com/w320/ru.png' },
                    { name: '勃兰登堡门', country: '德国', lat: 52.5163, lng: 13.3777, spotId: 13, flag: 'https://flagcdn.com/w320/de.png' },
                    { name: '基督像', country: '巴西', lat: -22.9519, lng: -43.2106, spotId: 14, flag: 'https://flagcdn.com/w320/br.png' },
                    { name: '雅典卫城', country: '希腊', lat: 37.9715, lng: 23.7257, spotId: 15, flag: 'https://flagcdn.com/w320/gr.png' },
                    { name: '尼亚加拉瀑布', country: '加拿大', lat: 43.0799, lng: -79.0747, spotId: 16, flag: 'https://flagcdn.com/w320/ca.png' },
                    { name: '好望角', country: '南非', lat: -34.3581, lng: 18.4719, spotId: 17, flag: 'https://flagcdn.com/w320/za.png' },
                    { name: '皇后镇', country: '新西兰', lat: -45.0312, lng: 168.6626, spotId: 18, flag: 'https://flagcdn.com/w320/nz.png' },
                    { name: '复活节岛', country: '智利', lat: -27.15, lng: -109.43, spotId: 19, flag: 'https://flagcdn.com/w320/cl.png' },
                    { name: '哈利法塔', country: '阿联酋', lat: 25.1972, lng: 55.2741, spotId: 20, flag: 'https://flagcdn.com/w320/ae.png' },
                ]
            },

            performSearch() {
                if (!this.cards || !this.cards.length) return
                if (!this.searchQuery.trim()) {
                    this.clearSearch()
                    return
                }
                const query = this.searchQuery.trim().toLowerCase()
                const matched = this.cards.find((card) => {
                    const label = (card.userData.label || '').toLowerCase()
                    const name = (card.userData.name || '').toLowerCase()
                    const country = (card.userData.country || '').toLowerCase()
                    return label.includes(query) || name.includes(query) || country.includes(query)
                })
                if (matched) {
                    this.rotateViewToPosition(matched.userData.pos)
                    this.selectedName = matched.userData.label || matched.userData.name
                } else {
                    alert('未找到匹配的国家或景点')
                }
            },

            onPortalSearch(q) {
                this.searchQuery = q || ''
                this.performSearch()
            },
            onPortalCat(type) {
                const names = { hotel: '酒店', flight: '机票', ai: 'AI 助手' }
                alert('「' + (names[type] || '该') + '」模块建设中，敬请期待')
            },
            onGoSpots() {
                this.clearSearch()
                alert('点击地球上的国家/景点热点，即可查看详情并购买门票')
            },
            clearSearch() {
                this.searchQuery = ''
                this.selectedName = ''
            },

            focusOnPosition(targetPos) {
                this.controls.target.copy(targetPos)
                this.controls.update()
            },

            // ====== 搜索定位：在原有视角下把目标旋转到屏幕正中心（不改动原定位方式） ======
            rotateViewToPosition(pos) {
                if (!this.camera || !this.controls || !pos) return
                const camPos = this.camera.position

                // 当前视角方向（相机→目标）
                const curDir = new THREE.Vector3().subVectors(this.controls.target, camPos)
                if (curDir.lengthSq() < 1e-8) return
                curDir.normalize()

                // 目标方向（相机→搜索到的位置）
                const targetDir = new THREE.Vector3().subVectors(pos, camPos)
                if (targetDir.lengthSq() < 1e-8) return
                targetDir.normalize()

                // 计算从当前视角方向旋转到目标方向的短路径四元数
                const quat = new THREE.Quaternion().setFromUnitVectors(curDir, targetDir)

                // 保持相机位置与视角距离不变，仅将控制目标点绕相机旋转
                // （等价于把地球/视图旋转，使目标位置到达屏幕正中）
                const offset = new THREE.Vector3().subVectors(this.controls.target, camPos)
                offset.applyQuaternion(quat)
                const endTarget = camPos.clone().add(offset)

                // 平滑旋转动画（约 0.9 秒）
                this._searchAnim = {
                    start: this.controls.target.clone(),
                    end: endTarget,
                    t: 0,
                    duration: 900,
                }
                this._searchAnimLast = performance.now()
            },

            // 在渲染循环中逐帧推进旋转动画
            applySearchAnimation() {
                const anim = this._searchAnim
                if (!anim) return
                const now = performance.now()
                const dt = (now - (this._searchAnimLast || now)) / anim.duration
                this._searchAnimLast = now
                anim.t = Math.min(1, anim.t + dt)
                const ease = 1 - Math.pow(1 - anim.t, 3) // easeOutCubic
                this.controls.target.lerpVectors(anim.start, anim.end, ease)
                this.controls.update()
                if (anim.t >= 1) {
                    this._searchAnim = null
                }
            },

            initScene() {
                const container = this.$refs.sceneContainer
                const width = container.clientWidth
                const height = container.clientHeight

                this.scene = new THREE.Scene()
                this.scene.background = new THREE.Color(0x050510)

                this.camera = new THREE.PerspectiveCamera(60, width / height, 0.1, 1000)
                this.camera.position.set(0, 0, 0)

                this.renderer = new THREE.WebGLRenderer({
                    antialias: true,
                    alpha: true,
                    powerPreference: 'high-performance',
                })
                this.renderer.setSize(width, height)
                this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
                this.renderer.toneMapping = THREE.ACESFilmicToneMapping
                this.renderer.toneMappingExposure = 1.2
                // 确保 canvas 可接收事件
                this.renderer.domElement.style.pointerEvents = 'auto'
                container.appendChild(this.renderer.domElement)

                this.labelRenderer = new CSS2DRenderer()
                this.labelRenderer.setSize(width, height)
                this.labelRenderer.domElement.style.position = 'absolute'
                this.labelRenderer.domElement.style.top = '0'
                this.labelRenderer.domElement.style.left = '0'
                this.labelRenderer.domElement.style.pointerEvents = 'none'
                container.appendChild(this.labelRenderer.domElement)

                this.controls = new OrbitControls(this.camera, this.renderer.domElement)
                this.controls.enableDamping = true
                this.controls.dampingFactor = 0.08
                this.controls.rotateSpeed = 0.8
                this.controls.enableZoom = false
                this.controls.enablePan = false
                this.controls.target.set(0, 0, -1)
                this.controls.minPolarAngle = 0.1
                this.controls.maxPolarAngle = Math.PI - 0.1
                this.controls.update()

                // 灯光
                const ambient = new THREE.AmbientLight(0x6666aa, 1.0)
                this.scene.add(ambient)
                const main = new THREE.DirectionalLight(0x88bbff, 2)
                main.position.set(10, 15, 10)
                this.scene.add(main)
                const back = new THREE.DirectionalLight(0xaa88ff, 1.5)
                back.position.set(-10, -5, -10)
                this.scene.add(back)
                const rim = new THREE.DirectionalLight(0x44ddff, 1)
                rim.position.set(0, 10, -10)
                this.scene.add(rim)

                // 装饰环
                const ringGeo = new THREE.RingGeometry(2.8, 3.0, 64)
                const ringMat = new THREE.MeshBasicMaterial({
                    color: 0x4488ff,
                    transparent: true,
                    opacity: 0.06,
                    side: THREE.DoubleSide,
                })
                const ring = new THREE.Mesh(ringGeo, ringMat)
                ring.rotation.x = -Math.PI / 2
                ring.position.y = -0.3
                this.scene.add(ring)

                this.cards = []
                this.particles = null
            },

            createBackground() {
                const panoramaUrl = require('@/assets/images/8k_earth_daymap.jpg')
                const fallbackGeo = new THREE.SphereGeometry(50, 128, 128)
                const fallbackMat = new THREE.MeshBasicMaterial({
                    color: 0x112244,
                    side: THREE.BackSide,
                })
                const fallbackSphere = new THREE.Mesh(fallbackGeo, fallbackMat)
                this.scene.add(fallbackSphere)
                this.panoramaSphere = fallbackSphere

                const textureLoader = new THREE.TextureLoader()
                const texture = textureLoader.load(
                    panoramaUrl,
                    () => console.log('✅ 地球纹理加载成功'),
                    undefined,
                    (err) => console.error('❌ 纹理加载失败', err)
                )

                texture.flipY = true
                texture.wrapS = THREE.RepeatWrapping
                texture.repeat.x = -1
                texture.colorSpace = THREE.SRGBColorSpace
                texture.minFilter = THREE.LinearMipmapLinearFilter
                texture.magFilter = THREE.LinearFilter
                texture.generateMipmaps = true
                texture.anisotropy = 16

                const mat = new THREE.MeshBasicMaterial({
                    map: texture,
                    side: THREE.BackSide,
                })
                fallbackSphere.material = mat
                fallbackSphere.rotation.set(0, 0, 0)
                fallbackSphere.scale.set(1, 1, 1)
            },

            createParticles() {
                const count = 2000
                const positions = new Float32Array(count * 3)
                const colors = new Float32Array(count * 3)

                for (let i = 0; i < count; i++) {
                    const radius = 30 + Math.random() * 70
                    const theta = Math.random() * Math.PI * 2
                    const phi = Math.acos(2 * Math.random() - 1)
                    positions[i * 3] = radius * Math.sin(phi) * Math.cos(theta)
                    positions[i * 3 + 1] = radius * Math.cos(phi) * 0.3
                    positions[i * 3 + 2] = radius * Math.sin(phi) * Math.sin(theta)
                    const c = new THREE.Color().setHSL(0.6 + Math.random() * 0.3, 0.8, 0.3 + Math.random() * 0.5)
                    colors[i * 3] = c.r
                    colors[i * 3 + 1] = c.g
                    colors[i * 3 + 2] = c.b
                }

                const geo = new THREE.BufferGeometry()
                geo.setAttribute('position', new THREE.BufferAttribute(positions, 3))
                geo.setAttribute('color', new THREE.BufferAttribute(colors, 3))

                const mat = new THREE.PointsMaterial({
                    size: 0.15,
                    vertexColors: true,
                    transparent: true,
                    opacity: 0.9,
                    blending: THREE.AdditiveBlending,
                    depthWrite: false,
                    sizeAttenuation: true,
                })

                this.particles = new THREE.Points(geo, mat)
                this.scene.add(this.particles)
            },

            createHotspots() {
                if (!this.hotspots.length) return

                const radius = 49.5

                const latLngToPosition = (lat, lng, r) => {
                    const phi = (90 - lat) * Math.PI / 180
                    const theta = lng * Math.PI / 180
                    return new THREE.Vector3(
                        r * Math.sin(phi) * Math.cos(theta),
                        r * Math.cos(phi),
                        r * Math.sin(phi) * Math.sin(theta)
                    )
                }

                this.hotspots.forEach((item) => {
                    const pos = latLngToPosition(item.lat, item.lng, radius)

                    const group = new THREE.Group()
                    group.position.copy(pos)
                    group.lookAt(0, 0, 0)

                    // 隐形大球（点击区域）
                    const hitGeo = new THREE.SphereGeometry(0.4, 8, 8)
                    const hitMat = new THREE.MeshBasicMaterial({
                        color: 0xffffff,
                        transparent: true,
                        opacity: 0,
                        depthTest: true,
                        depthWrite: false,
                    })
                    const hitSphere = new THREE.Mesh(hitGeo, hitMat)
                    group.add(hitSphere)

                    // 发光圆环
                    const ringGeo = new THREE.RingGeometry(0.2, 0.35, 32)
                    const ringMat = new THREE.MeshBasicMaterial({
                        color: 0x66ccff,
                        transparent: true,
                        opacity: 0.9,
                        side: THREE.DoubleSide,
                    })
                    const ring = new THREE.Mesh(ringGeo, ringMat)
                    group.add(ring)

                    // 内圈光晕
                    const innerGlow = new THREE.RingGeometry(0.1, 0.2, 16)
                    const glowMat = new THREE.MeshBasicMaterial({
                        color: 0x88ddff,
                        transparent: true,
                        opacity: 0.7,
                        side: THREE.DoubleSide,
                    })
                    const glowMesh = new THREE.Mesh(innerGlow, glowMat)
                    glowMesh.position.z = 0.001
                    group.add(glowMesh)

                    // 国旗 + 景点 (国家)
                    const labelDiv = document.createElement('div')
                    labelDiv.style.cssText = `
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    pointer-events: none;
                    user-select: none;
                    font-family: 'Segoe UI', 'PingFang SC', sans-serif;
                `
                    const img = document.createElement('img')
                    img.src = item.flag
                    img.style.cssText = `
                    width: 40px;
                    height: 26px;
                    border-radius: 3px;
                    border: 1px solid rgba(255,255,255,0.4);
                    box-shadow: 0 0 12px rgba(68,136,255,0.5);
                    object-fit: cover;
                    margin-bottom: 4px;
                `
                    const nameSpan = document.createElement('span')
                    nameSpan.textContent = `${item.name} (${item.country})`
                    nameSpan.style.cssText = `
                    color: #fff;
                    font-size: 13px;
                    font-weight: 700;
                    text-shadow: 0 0 8px rgba(0,0,0,0.9);
                    background: rgba(0,0,0,0.6);
                    padding: 3px 10px;
                    border-radius: 12px;
                    border: 1px solid rgba(255,255,255,0.3);
                    backdrop-filter: blur(2px);
                    letter-spacing: 0.5px;
                    white-space: nowrap;
                `
                    labelDiv.appendChild(img)
                    labelDiv.appendChild(nameSpan)

                    const label = new CSS2DObject(labelDiv)
                    label.position.set(0, -0.45, 0)
                    group.add(label)

                    group.userData = {
                        isCard: true,
                        spotId: item.spotId,
                        pos: pos.clone(),
                        name: item.name,
                        country: item.country,
                        label: `${item.name} (${item.country})`,
                    }

                    this.scene.add(group)
                    this.cards.push(group)
                })

                this.setupInteraction()
            },

            setupInteraction() {
                const raycaster = new THREE.Raycaster()
                const mouse = new THREE.Vector2()

                // 递归查找父级 isCard 组
                const findCardGroup = (object) => {
                    let current = object
                    while (current) {
                        if (current.userData && current.userData.isCard) return current
                        current = current.parent
                    }
                    return null
                }

                const handleClick = (event) => {
                    const rect = this.renderer.domElement.getBoundingClientRect()
                    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
                    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

                    raycaster.setFromCamera(mouse, this.camera)
                    // 检测场景中所有物体，但过滤出属于 cards 的组
                    const intersects = raycaster.intersectObjects(this.scene.children, true)
                    const card = intersects.length > 0 ? findCardGroup(intersects[0].object) : null

                    if (card) {
                        const spotId = card.userData.spotId
                        this.selectedName = card.userData.label || card.userData.name
                        this.$router.push(spotId === 1
                            ? { path: '/spot/1', query: { v: '1' } }
                            : `/spot/${spotId}`)
                    }
                }

                const handlePointerMove = (event) => {
                    const rect = this.renderer.domElement.getBoundingClientRect()
                    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
                    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

                    raycaster.setFromCamera(mouse, this.camera)
                    const intersects = raycaster.intersectObjects(this.scene.children, true)
                    const card = intersects.length > 0 ? findCardGroup(intersects[0].object) : null

                    if (card !== this.hoveredCard) {
                        if (this.hoveredCard) this.resetCardHighlight(this.hoveredCard)
                        if (card) this.highlightCard(card)
                        this.renderer.domElement.style.cursor = card ? 'pointer' : 'default'
                        this.hoveredCard = card
                    }
                }

                // 同时绑定 click 和 pointerdown，保证兼容性
                this.renderer.domElement.addEventListener('click', handleClick)
                this.renderer.domElement.addEventListener('pointermove', handlePointerMove)

                this._cleanupInteraction = () => {
                    this.renderer.domElement.removeEventListener('click', handleClick)
                    this.renderer.domElement.removeEventListener('pointermove', handlePointerMove)
                }
            },

            highlightCard(card) {
                const ring = card.children.find(c => c.isMesh && c.geometry.type === 'RingGeometry')
                if (ring) {
                    ring.material.color.setHex(0xffaa44)
                    ring.material.opacity = 1.0
                }
                const label = card.children.find(c => c.isCSS2DObject)
                if (label) {
                    const img = label.element.querySelector('img')
                    const span = label.element.querySelector('span')
                    if (img) img.style.borderColor = 'rgba(255,170,68,0.8)'
                    if (span) span.style.borderColor = 'rgba(255,170,68,0.6)'
                }
            },

            resetCardHighlight(card) {
                const ring = card.children.find(c => c.isMesh && c.geometry.type === 'RingGeometry')
                if (ring) {
                    ring.material.color.setHex(0x66ccff)
                    ring.material.opacity = 0.85
                }
                const label = card.children.find(c => c.isCSS2DObject)
                if (label) {
                    const img = label.element.querySelector('img')
                    const span = label.element.querySelector('span')
                    if (img) img.style.borderColor = 'rgba(255,255,255,0.2)'
                    if (span) span.style.borderColor = 'rgba(255,255,255,0.15)'
                }
            },

            // ====== 天气云层（程序化生成，无需外部贴图） ======

            // ====== 天气加载与标签 ======
            async loadWeather() {
                try {
                    const points = this.hotspots.map(h => ({ spotId: h.spotId, name: h.name, lat: h.lat, lng: h.lng }))
                    const res = await getWeatherBatch(points)
                    const list = (res && res.data) ? res.data : []
                    list.forEach(w => {
                        if (w && w.spotId != null) this.weatherMap[w.spotId] = w
                    })
                    this.addWeatherLabels()
                    this.refreshWeatherAlerts(list)
                } catch (e) {
                    console.error('获取天气失败', e)
                }
            },

            addWeatherLabels() {
                this.weatherLabels.forEach((l) => { if (l && this.scene) this.scene.remove(l) })
                this.weatherLabels = []
                this.cards.forEach((card) => {
                    const spotId = card.userData.spotId
                    const w = this.weatherMap[spotId]
                    if (!w) return
                    const wtype = (w.text || '').includes('雨') ? 'rain' : (w.text || '').includes('雪') ? 'snow' : (w.text || '').includes('雷') ? 'storm' : (w.text || '').includes('晴') ? 'sun' : ((w.text || '').includes('云') || (w.text || '').includes('阴')) ? 'cloud' : 'def'
                    const div = document.createElement('div')
                    div.className = 'weather-label weather-label--' + wtype
                    div.textContent = (w.icon || '') + ' ' + (w.temp != null ? Math.round(Number(w.temp)) + '°C' : '') + ' ' + (w.text || '')
                    div.title = '点击查看天气分析'
                    div.style.pointerEvents = 'auto'
                    div.style.cursor = 'pointer'
                    div.onclick = (e) => {
                        e.stopPropagation()
                        this.showWeatherPanel(card)
                    }
                    const label = new CSS2DObject(div)
                    label.position.set(0, 1.05, 0)
                    card.add(label)
                    this.weatherLabels.push(label)
                })
            },

            // ====== 天气标签屏幕空间自动避让（不压国家图标、彼此不重叠） ======
            layoutWeatherLabels() {
                if (!this.weatherLabels.length) return
                const container = this.$refs.sceneContainer
                const W = container.clientWidth
                const H = container.clientHeight
                if (!W || !H) return
                const proj = (obj) => {
                    const v = new THREE.Vector3()
                    obj.getWorldPosition(v)
                    v.project(this.camera)
                    return { x: (v.x * 0.5 + 0.5) * W, y: (-v.y * 0.5 + 0.5) * H }
                }
                // 固定障碍：各国图标标签（flag+名字）
                const fixed = []
                this.cards.forEach((card) => {
                    const main = card.children.find(c => c.isCSS2DObject)
                    if (!main) return
                    const p = proj(main)
                    const el = main.element
                    fixed.push({ x: p.x - el.offsetWidth / 2, y: p.y - el.offsetHeight / 2, w: el.offsetWidth || 90, h: el.offsetHeight || 24, fixed: true })
                })
                // 可动：天气标签
                const boxes = []
                this.weatherLabels.forEach((l) => {
                    const p = proj(l)
                    const el = l.element
                    boxes.push({ l, x: p.x - el.offsetWidth / 2, y: p.y - el.offsetHeight / 2, w: el.offsetWidth || 70, h: el.offsetHeight || 24, ox: 0, oy: 0 })
                })
                // 迭代分离
                for (let iter = 0; iter < 16; iter++) {
                    let moved = false
                    const all = fixed.concat(boxes)
                    for (let i = 0; i < boxes.length; i++) {
                        const a = boxes[i]
                        for (let j = 0; j < all.length; j++) {
                            if (all[j] === a) continue
                            const b = all[j]
                            const ax = a.x + a.ox, ay = a.y + a.oy
                            const bx = b.x + (b.ox || 0), by = b.y + (b.oy || 0)
                            if (ax < bx + b.w && ax + a.w > bx && ay < by + b.h && ay + a.h > by) {
                                const dx = (bx + b.w / 2) - (ax + a.w / 2)
                                const dy = (by + b.h / 2) - (ay + a.h / 2)
                                const adx = Math.abs(dx), ady = Math.abs(dy)
                                const push = 3
                                if (adx > ady) {
                                    a.ox += (dx >= 0 ? -1 : 1) * push
                                } else {
                                    a.oy += (dy >= 0 ? -1 : 1) * push
                                }
                                moved = true
                            }
                        }
                    }
                    if (!moved) break
                }
                boxes.forEach((b) => {
                    if (b.l.element) {
                        b.l.element.style.marginLeft = Math.round(b.ox) + 'px'
                        b.l.element.style.marginTop = Math.round(b.oy) + 'px'
                    }
                })
            },
            // ====== 天气面板 ======
            async showWeatherPanel(card) {
                const spotId = card.userData.spotId
                const spot = this.hotspots.find(h => h.spotId === spotId) || {}
                const w = this.weatherMap[spotId] || {}
                this.weatherPanel = {
                    spotId,
                    name: card.userData.name || w.name || spot.name,
                    country: card.userData.country || spot.country || '',
                    weather: w,
                    loading: !w.temp,
                }
                if (!w.temp) {
                    try {
                        const now = await getWeatherNow(spot.lat, spot.lng, spotId, spot.name)
                        if (now && now.data) {
                            this.weatherMap[spotId] = now.data
                            this.weatherPanel.weather = now.data
                            this.weatherPanel.loading = false
                            this.addWeatherLabels()
                        }
                    } catch (e) {
                        this.weatherPanel.loading = false
                    }
                }
            },

            openWeatherFromTicker(spotId) {
                this.showWeatherPanel({ userData: { spotId: spotId, name: (this.weatherMap[spotId] || {}).name || '' } })
            },

            closeWeatherPanel() {
                this.weatherPanel = null
            },

            goSpotDetail() {
                if (this.weatherPanel) {
                    this.$router.push('/spot/' + this.weatherPanel.spotId)
                }
            },

            // ====== 天气提醒推送（WebSocket） ======
            initWeatherPush() {
                try {
                    const proto = location.protocol === 'https:' ? 'wss://' : 'ws://'
                    const ws = new WebSocket(proto + location.host + '/ws/weather')
                    this.wsWeather = ws
                    ws.onopen = () => {
                        const points = this.hotspots.map(h => ({ spotId: h.spotId, name: h.name, lat: h.lat, lng: h.lng }))
                        ws.send(JSON.stringify({ type: 'subscribe', points }))
                    }
                    ws.onmessage = (ev) => {
                        try {
                            const msg = JSON.parse(ev.data)
                            if (msg && msg.type === 'weather_alerts' && Array.isArray(msg.alerts)) {
                                this.weatherAlerts = msg.alerts
                            }
                        } catch (e) { }
                    }
                    ws.onclose = () => {
                        this.wsWeather = null
                    }
                    this.weatherAlertTimer = setInterval(() => {
                        if (this.weatherAlerts.length > 1) {
                            this.weatherAlertIndex = (this.weatherAlertIndex + 1) % this.weatherAlerts.length
                        }
                    }, 5000)
                } catch (e) {
                    console.error('天气推送连接失败', e)
                }
            },

            refreshWeatherAlerts(list) {
                const alerts = (list || []).filter(w => w && w.alert).map(w => w.name + '：' + w.alert)
                if (alerts.length) this.weatherAlerts = alerts
            },
            animate() {
                this.animationId = requestAnimationFrame(this.animate)

                // 搜索定位动画：逐帧把目标旋转到屏幕中心
                this.applySearchAnimation()

                if (this.particles) {
                    this.particles.rotation.y += 0.0001
                    this.particles.rotation.x += 0.00002
                }

                const time = Date.now() * 0.002
                this.cards.forEach((card, index) => {
                    const ring = card.children.find(c => c.isMesh && c.geometry.type === 'RingGeometry')
                    if (ring) {
                        const scale = 1 + 0.12 * Math.sin(time + index)
                        ring.scale.set(scale, scale, 1)
                    }
                })

                this.controls.update()
                this.renderer.render(this.scene, this.camera)
                this.labelRenderer.render(this.scene, this.camera)
                this.layoutWeatherLabels()
            },

            onResize() {
                const container = this.$refs.sceneContainer
                const width = container.clientWidth
                const height = container.clientHeight
                this.camera.aspect = width / height
                this.camera.updateProjectionMatrix()
                this.renderer.setSize(width, height)
                this.labelRenderer.setSize(width, height)
            },
        },
    }
</script>

<style scoped>
    .home-container {
        width: 100vw;
        height: 100vh;
        overflow: hidden;
        background: #050510;
        position: relative;
    }
    .scene-container {
        width: 100%;
        height: 100%;
        position: absolute;
        top: 0;
        left: 0;
    }
    .hud-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 10;
    }
    .hud-top-left {
        position: absolute;
        top: 30px;
        left: 30px;
        pointer-events: none;
    }
    .hud-title {
        color: #66ccff;
        font-size: 22px;
        font-weight: 700;
        letter-spacing: 4px;
        text-shadow: 0 0 30px rgba(68, 136, 255, 0.3);
        font-family: 'Segoe UI', 'PingFang SC', sans-serif;
    }
    .hud-subtitle {
        color: rgba(68, 136, 255, 0.5);
        font-size: 11px;
        letter-spacing: 6px;
        margin-top: 2px;
        font-family: 'Courier New', monospace;
    }
    .search-box {
        margin-top: 12px;
        display: flex;
        align-items: center;
        gap: 6px;
        pointer-events: auto;
    }
    .search-box input {
        padding: 6px 12px;
        border: 1px solid rgba(255,255,255,0.2);
        border-radius: 20px;
        background: rgba(0,0,0,0.4);
        backdrop-filter: blur(4px);
        color: #fff;
        font-size: 14px;
        width: 200px;
        outline: none;
        transition: border-color 0.3s;
        font-family: 'Segoe UI', 'PingFang SC', sans-serif;
    }
    .search-box input::placeholder {
        color: rgba(255,255,255,0.5);
    }
    .search-box input:focus {
        border-color: #66ccff;
    }
    .search-box button {
        background: rgba(0,0,0,0.4);
        backdrop-filter: blur(4px);
        border: 1px solid rgba(255,255,255,0.2);
        border-radius: 20px;
        color: #fff;
        padding: 6px 12px;
        cursor: pointer;
        font-size: 14px;
        transition: background 0.3s;
        pointer-events: auto;
    }
    .search-box button:hover {
        background: rgba(0,0,0,0.6);
    }
    .search-box .clear-btn {
        background: transparent;
        border: none;
        color: rgba(255,255,255,0.6);
        padding: 0 4px;
        font-size: 16px;
    }
    .search-box .clear-btn:hover {
        color: #fff;
    }
    .hud-top-right {
        position: absolute;
        top: 30px;
        right: 30px;
        text-align: right;
        pointer-events: none;
    }
    .hud-stats {
        color: rgba(136, 204, 255, 0.7);
        font-size: 13px;
        letter-spacing: 1px;
        font-family: 'Courier New', monospace;
    }
    .stat-label {
        color: rgba(136, 204, 255, 0.4);
        margin-right: 10px;
    }
    .stat-value {
        color: #66ccff;
        font-weight: 600;
        font-size: 16px;
    }
    .stat-value.highlight {
        color: #ffcc44;
        text-shadow: 0 0 20px rgba(255, 204, 68, 0.3);
    }
    .hud-bottom {
        position: absolute;
        bottom: 80px;
        left: 50%;
        transform: translateX(-50%);
        pointer-events: none;
    }
    .hud-control-hint {
        display: flex;
        gap: 30px;
        background: rgba(0, 10, 30, 0.4);
        backdrop-filter: blur(10px);
        padding: 10px 24px;
        border-radius: 30px;
        border: 1px solid rgba(68, 136, 255, 0.15);
    }
    .key {
        color: rgba(136, 204, 255, 0.6);
        font-size: 12px;
        letter-spacing: 1px;
        font-family: 'Courier New', monospace;
    }
    .key::before {
        content: '◆ ';
        color: rgba(68, 136, 255, 0.3);
        font-size: 8px;
    }
    .home-container :deep(.van-tabbar) {
        position: absolute;
        bottom: 0;
        left: 0;
        width: 100%;
        z-index: 20;
        background: rgba(5, 5, 16, 0.6) !important;
        backdrop-filter: blur(20px) !important;
        border-top: 1px solid rgba(68, 136, 255, 0.1) !important;
    }
    .home-container :deep(.van-tabbar-item) {
        color: rgba(136, 204, 255, 0.5) !important;
    }
    .home-container :deep(.van-tabbar-item--active) {
        color: #66ccff !important;
    }
    @media (max-width: 768px) {
        .hud-top-left { top: 16px; left: 16px; }
        .hud-title { font-size: 16px; }
        .hud-subtitle { font-size: 9px; }
        .search-box input { width: 140px; font-size: 12px; }
        .hud-top-right { top: 16px; right: 16px; }
        .hud-stats { font-size: 11px; }
        .hud-bottom { bottom: 70px; }
        .hud-control-hint { gap: 14px; padding: 6px 14px; }
        .key { font-size: 10px; }
    }

    /* ===== 顶部公告栏 ===== */
    .notice-bar {
        position: absolute;
        top: 14px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 20;
        display: flex;
        align-items: center;
        gap: 12px;
        max-width: 62vw;
        padding: 7px 18px;
        border-radius: 30px;
        background: rgba(5, 15, 30, 0.55);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        border: 1px solid rgba(200, 162, 74, 0.35);
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
        cursor: pointer;
        pointer-events: auto;
        transition: border-color 0.3s, box-shadow 0.3s;
    }
    .notice-bar:hover {
        border-color: rgba(200, 162, 74, 0.7);
        box-shadow: 0 6px 26px rgba(0, 0, 0, 0.45);
    }
    .notice-bar-tag {
        flex-shrink: 0;
        font-size: 12px;
        color: #1a1206;
        background: linear-gradient(135deg, #e5c97b, #c8a24a);
        border-radius: 20px;
        padding: 2px 10px;
        font-weight: 600;
        letter-spacing: 1px;
        white-space: nowrap;
    }
    .notice-bar-text {
        color: #e8eef8;
        font-size: 13px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 42vw;
    }
    .notice-bar-more {
        flex-shrink: 0;
        color: rgba(102, 204, 255, 0.85);
        font-size: 12px;
        white-space: nowrap;
    }
    .notice-fade-enter-active,
    .notice-fade-leave-active {
        transition: opacity 0.4s ease;
    }
    .notice-fade-enter-from,
    .notice-fade-leave-to {
        opacity: 0;
    }
    /* ===== 天气标签（CSS2D） ===== */
    .weather-label {
        color: #fff;
        font-size: 12px;
        font-weight: 700;
        background: rgba(8, 20, 40, 0.85);
        border: 1px solid rgba(102, 204, 255, 0.6);
        border-radius: 14px;
        padding: 2px 8px;
        white-space: nowrap;
        backdrop-filter: blur(4px);
        box-shadow: 0 0 10px rgba(68, 136, 255, 0.4);
        pointer-events: none;
    }
    .weather-label:hover {
        border-color: #ffcc44;
        box-shadow: 0 0 14px rgba(255, 204, 68, 0.6);
    }
    /* ===== 天气提醒条 ===== */
    .weather-alert-bar {
        position: absolute;
        top: 60px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 22;
        display: flex;
        align-items: center;
        gap: 10px;
        max-width: 60vw;
        padding: 6px 16px;
        border-radius: 24px;
        background: rgba(30, 15, 5, 0.6);
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 170, 60, 0.4);
        pointer-events: none;
    }
    .weather-alert-tag {
        flex-shrink: 0;
        font-size: 12px;
        color: #1a1206;
        background: linear-gradient(135deg, #ffd27a, #ff9f43);
        border-radius: 20px;
        padding: 2px 10px;
        font-weight: 600;
        white-space: nowrap;
    }
    .weather-alert-text {
        color: #ffe8c2;
        font-size: 13px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 42vw;
    }
    /* ===== 天气面板 ===== */
    .weather-panel-mask {
        position: absolute;
        inset: 0;
        z-index: 30;
        background: rgba(2, 6, 16, 0.55);
        backdrop-filter: blur(4px);
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: auto;
    }
    .weather-panel {
        position: relative;
        width: 320px;
        max-width: 86vw;
        max-height: 80vh;
        overflow: auto;
        background: rgba(8, 18, 38, 0.92);
        border: 1px solid rgba(102, 204, 255, 0.35);
        border-radius: 18px;
        padding: 22px 20px 18px;
        box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6);
        color: #e8eef8;
    }
    .weather-panel-close {
        position: absolute;
        top: 10px;
        right: 12px;
        background: transparent;
        border: none;
        color: rgba(255, 255, 255, 0.6);
        font-size: 16px;
        cursor: pointer;
    }
    .weather-panel-title {
        font-size: 17px;
        font-weight: 700;
        color: #fff;
        letter-spacing: 1px;
    }
    .weather-panel-country {
        font-size: 12px;
        color: rgba(136, 204, 255, 0.7);
        margin-left: 6px;
        font-weight: 400;
    }
    .weather-panel-loading {
        color: rgba(255, 255, 255, 0.6);
        margin-top: 14px;
    }
    .weather-panel-main {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-top: 14px;
    }
    .weather-panel-icon { font-size: 42px; }
    .weather-panel-temp { font-size: 38px; font-weight: 700; color: #fff; }
    .weather-panel-text { font-size: 15px; color: #9fd8ff; }
    .weather-panel-detail {
        display: flex;
        gap: 14px;
        margin-top: 10px;
        font-size: 12px;
        color: rgba(232, 238, 248, 0.75);
    }
    .weather-panel-advice {
        margin-top: 12px;
        padding: 10px 12px;
        background: rgba(102, 204, 255, 0.08);
        border-radius: 10px;
        font-size: 13px;
        line-height: 1.8;
    }
    .weather-panel-alert { color: #ffb36b; font-weight: 600; }
    .weather-panel-forecast { margin-top: 12px; }
    .forecast-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 6px 0;
        border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
        font-size: 13px;
    }
    .forecast-date { width: 44px; color: rgba(255, 255, 255, 0.6); }
    .forecast-icon { width: 24px; text-align: center; }
    .forecast-text { flex: 1; }
    .forecast-temp { color: #9fd8ff; }
    .weather-panel-btn {
        margin-top: 14px;
        width: 100%;
        padding: 10px;
        border: none;
        border-radius: 12px;
        background: linear-gradient(135deg, #2f6bff, #1f9dff);
        color: #fff;
        font-size: 14px;
        font-weight: 600;
        cursor: pointer;
    }
    @media (max-width: 768px) {
        .weather-alert-bar { top: 54px; }
    }
    /* ===== 天气标签醒目化（覆盖） ===== */
    .weather-label {
        font-size: 15px !important;
        font-weight: 800 !important;
        padding: 4px 12px !important;
        border-width: 2px !important;
        border-radius: 16px !important;
        letter-spacing: 0.5px;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);
        transition: transform 0.2s, box-shadow 0.2s;
    }
    .weather-label--sun { background: linear-gradient(135deg, rgba(255,180,50,0.95), rgba(255,120,40,0.9)) !important; border-color: #ffd27a !important; }
    .weather-label--rain { background: linear-gradient(135deg, rgba(40,120,255,0.95), rgba(60,80,200,0.9)) !important; border-color: #7ab8ff !important; }
    .weather-label--snow { background: linear-gradient(135deg, rgba(120,190,255,0.95), rgba(180,220,255,0.9)) !important; border-color: #dff0ff !important; color: #10233f !important; }
    .weather-label--storm { background: linear-gradient(135deg, rgba(120,60,200,0.95), rgba(80,40,160,0.9)) !important; border-color: #c9a6ff !important; }
    .weather-label--cloud { background: linear-gradient(135deg, rgba(90,110,140,0.95), rgba(60,75,100,0.9)) !important; border-color: #aabdd6 !important; }
    .weather-label--def { background: linear-gradient(135deg, rgba(30,60,110,0.95), rgba(20,40,80,0.9)) !important; border-color: #66ccff !important; }
    .weather-label:hover {
        border-color: #ffcc44 !important;
        box-shadow: 0 0 18px rgba(255, 204, 68, 0.8) !important;
        transform: scale(1.08);
    }
    /* ===== 全球天气一览（地球下方滚动栏） ===== */
    .weather-ticker {
        position: absolute;
        left: 50%;
        transform: translateX(-50%);
        bottom: 58px;
        z-index: 24;
        display: flex;
        align-items: center;
        gap: 10px;
        width: min(92vw, 820px);
        padding: 6px 10px;
        border-radius: 16px;
        background: rgba(5, 15, 30, 0.6);
        backdrop-filter: blur(12px);
        -webkit-backdrop-filter: blur(12px);
        border: 1px solid rgba(102, 204, 255, 0.25);
        pointer-events: auto;
    }
    .weather-ticker-title {
        flex-shrink: 0;
        font-size: 12px;
        font-weight: 700;
        color: #66ccff;
        letter-spacing: 1px;
        white-space: nowrap;
    }
    .weather-ticker-scroll {
        display: flex;
        gap: 8px;
        overflow-x: auto;
        scrollbar-width: none;
        -webkit-overflow-scrolling: touch;
        flex: 1;
    }
    .weather-ticker-scroll::-webkit-scrollbar { display: none; }
    .weather-ticker-item {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        gap: 4px;
        padding: 3px 10px;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.08);
        border: 1px solid rgba(255, 255, 255, 0.12);
        cursor: pointer;
        white-space: nowrap;
        transition: all 0.2s;
    }
    .weather-ticker-item:hover {
        background: rgba(102, 204, 255, 0.18);
        border-color: #66ccff;
    }
    .ticker-icon { font-size: 14px; }
    .ticker-name { font-size: 12px; color: #dbe9ff; max-width: 90px; overflow: hidden; text-overflow: ellipsis; }
    .ticker-temp { font-size: 12px; font-weight: 700; color: #fff; }
    @media (max-width: 768px) {
        .weather-ticker { bottom: 50px; width: 96vw; }
        .weather-ticker-title { display: none; }
    }
</style>
<style>
    /* ===== 动态元素全局样式（JS createElement 生成，scoped 样式不生效，必须全局） ===== */

    .weather-label {
        color: #fff;
        font-size: 15px;
        font-weight: 800;
        letter-spacing: 0.5px;
        padding: 4px 12px;
        border-radius: 16px;
        border: 2px solid rgba(102, 204, 255, 0.9);
        white-space: nowrap;
        backdrop-filter: blur(6px);
        -webkit-backdrop-filter: blur(6px);
        box-shadow: 0 0 16px rgba(68, 136, 255, 0.55);
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);
        transition: transform 0.2s, box-shadow 0.2s;
        pointer-events: auto;
        cursor: pointer;
    }
    .weather-label--sun { background: linear-gradient(135deg, rgba(255,180,50,0.95), rgba(255,120,40,0.9)); border-color: #ffd27a; }
    .weather-label--rain { background: linear-gradient(135deg, rgba(40,120,255,0.95), rgba(60,80,200,0.9)); border-color: #7ab8ff; }
    .weather-label--snow { background: linear-gradient(135deg, rgba(120,190,255,0.95), rgba(180,220,255,0.9)); border-color: #dff0ff; color: #10233f; }
    .weather-label--storm { background: linear-gradient(135deg, rgba(120,60,200,0.95), rgba(80,40,160,0.9)); border-color: #c9a6ff; }
    .weather-label--cloud { background: linear-gradient(135deg, rgba(90,110,140,0.95), rgba(60,75,100,0.9)); border-color: #aabdd6; }
    .weather-label--def { background: linear-gradient(135deg, rgba(30,60,110,0.95), rgba(20,40,80,0.9)); border-color: #66ccff; }
    .weather-label:hover {
        border-color: #ffcc44;
        box-shadow: 0 0 18px rgba(255, 204, 68, 0.8);
        transform: scale(1.08);
    }
</style>