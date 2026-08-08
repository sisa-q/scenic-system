<template>
    <div class="login-wrapper">
        <!-- 万花筒背景画布 -->
        <canvas ref="kaleidoscopeCanvas" class="kaleidoscope-bg"></canvas>

        <!-- 登录/注册卡片（毛玻璃效果） -->
        <div class="login-box glassmorphism">
            <h2>🏞️ 智慧景区</h2>

            <!-- 模式切换 -->
            <div class="tab-switch">
                <span :class="{ active: isLoginMode }" @click="isLoginMode = true">登录</span>
                <span :class="{ active: !isLoginMode }" @click="isLoginMode = false">注册</span>
            </div>

            <!-- 登录表单 -->
            <div v-if="isLoginMode">
                <van-field
                        v-model="loginForm.username"
                        label="账号"
                        placeholder="用户名或手机号"
                        type="text"
                />
                <van-field
                        v-model="loginForm.password"
                        label="密码"
                        placeholder="密码（至少6位）"
                        type="password"
                />
                <div style="margin: 16px 0">
                    <van-button
                            type="primary"
                            block
                            round
                            @click="onLogin"
                            :loading="loginLoading"
                    >
                        {{ loginLoading ? '登录中...' : '登录' }}
                    </van-button>
                </div>
            </div>

            <!-- 注册表单 -->
            <div v-else>
                <van-field
                        v-model="registerForm.username"
                        label="用户名"
                        placeholder="4-20位字母或数字"
                        type="text"
                />
                <van-field
                        v-model="registerForm.password"
                        label="密码"
                        placeholder="至少6位"
                        type="password"
                />
                <van-field
                        v-model="registerForm.nickname"
                        label="昵称"
                        placeholder="选填"
                        type="text"
                />
                <van-field
                        v-model="registerForm.phone"
                        label="手机号"
                        placeholder="选填"
                        type="tel"
                />
                <div style="margin: 16px 0">
                    <van-button
                            type="primary"
                            block
                            round
                            @click="onRegister"
                            :loading="registerLoading"
                    >
                        {{ registerLoading ? '注册中...' : '立即注册' }}
                    </van-button>
                </div>
                <div class="switch-hint">
                    已有账号？
                    <span @click="isLoginMode = true">去登录</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import { useUserStore } from '@/store/modules/user'
    import { registerApi } from '@/api/user'
    import { getSpotList } from '@/api/ticket'
    import { isValidPhone } from '@/utils/validator'
    import { showToast } from 'vant'

    export default {
        name: 'WebLogin',
        data() {
            return {
                // 表单状态
                isLoginMode: true,
                loginLoading: false,
                registerLoading: false,
                loginForm: { username: '', password: '' },
                registerForm: { username: '', password: '', nickname: '', phone: '' },

                // Canvas 动画参数
                canvas: null,
                ctx: null,
                animationId: null,
                angle: 0,
                petals: 24,
                subLayers: 4,
                images: [],
                imagePool: [],

                // 缓存几何参数
                cx: 0,
                cy: 0,
                maxR: 0,
                angleStep: 0,
                vignetteGrad: null,
                centerGrad: null,

                // 帧率控制
                lastFrameTime: 0,
                fpsInterval: 1000 / 30,
            }
        },

        async mounted() {
            await this.loadSpotImages()
            this.canvas = this.$refs.kaleidoscopeCanvas
            this.ctx = this.canvas.getContext('2d')
            this.resizeCanvas()
            window.addEventListener('resize', this.resizeCanvas)
            this.animationId = requestAnimationFrame((t) => this.drawKaleidoscope(t))
        },

        beforeUnmount() {
            window.removeEventListener('resize', this.resizeCanvas)
            cancelAnimationFrame(this.animationId)
        },

        methods: {
            // ---------- 图片加载（智能降级：优先网络图片，失败则生成彩色纹理） ----------
            async loadSpotImages() {
                try {
                    // 尝试从后端获取景点列表
                    const res = await getSpotList()
                    const spots = res.data || []
                    const totalNeeded = this.petals * this.subLayers // 96

                    // 构建图片 URL 列表
                    const urls = []
                    spots.forEach((spot) => {
                        if (urls.length < totalNeeded) {
                            const url = spot.imageUrl || `https://picsum.photos/seed/${spot.id}/1200/1200`
                            urls.push(url)
                        }
                    })

                    // 补足占位图
                    let seed = 1000
                    while (urls.length < totalNeeded) {
                        urls.push(`https://picsum.photos/seed/extra${seed++}/1200/1200`)
                    }

                    // 预加载图片（设置超时机制，5秒内未加载完成则放弃）
                    const loadImageWithTimeout = (url, timeout = 5000) =>
                        new Promise((resolve) => {
                            const img = new Image()
                            img.crossOrigin = 'anonymous'
                            let timer = setTimeout(() => {
                                img.src = '' // 取消加载
                                resolve(null)
                            }, timeout)
                            img.onload = () => {
                                clearTimeout(timer)
                                resolve(img)
                            }
                            img.onerror = () => {
                                clearTimeout(timer)
                                resolve(null)
                            }
                            img.src = url
                        })

                    const loaded = await Promise.all(urls.map((url) => loadImageWithTimeout(url)))
                    const validImages = loaded.filter((img) => img !== null)

                    if (validImages.length === 0) {
                        // 如果一张图片都没加载成功，生成彩色纹理
                        this.generateFallbackImages()
                    } else {
                        // 增强图片并保存
                        this.images = validImages.map((img) => this.enhanceImage(img))
                        // 如果图片数量不足，用生成的纹理补足
                        if (this.images.length < totalNeeded) {
                            const extra = this.generateTextureImages(totalNeeded - this.images.length)
                            this.images = this.images.concat(extra)
                        }
                        console.log(`✅ 成功加载 ${this.images.length} 张图片（包含网络图片和后备纹理）`)
                    }
                } catch (e) {
                    console.warn('加载网络图片失败，启用完全后备纹理', e)
                    this.generateFallbackImages()
                }

                // 确保 images 至少有一张图片（最终降级）
                if (this.images.length === 0) {
                    this.generateFallbackImages()
                }
            },

            // 生成彩色纹理图片（无需网络）
            generateTextureImages(count) {
                const images = []
                const canvas = document.createElement('canvas')
                canvas.width = 512
                canvas.height = 512
                const ctx = canvas.getContext('2d')

                for (let i = 0; i < count; i++) {
                    const hue = (i * 15) % 360
                    const grad = ctx.createRadialGradient(256, 256, 0, 256, 256, 256)
                    grad.addColorStop(0, `hsl(${hue}, 90%, 70%)`)
                    grad.addColorStop(0.5, `hsl(${(hue + 40) % 360}, 80%, 50%)`)
                    grad.addColorStop(1, `hsl(${(hue + 80) % 360}, 70%, 30%)`)
                    ctx.fillStyle = grad
                    ctx.fillRect(0, 0, 512, 512)
                    // 添加随机纹理点
                    ctx.fillStyle = `hsla(${(hue + 120) % 360}, 80%, 80%, 0.3)`
                    for (let j = 0; j < 30; j++) {
                        const x = Math.random() * 512
                        const y = Math.random() * 512
                        const r = 10 + Math.random() * 60
                        ctx.beginPath()
                        ctx.arc(x, y, r, 0, Math.PI * 2)
                        ctx.fill()
                    }
                    const img = new Image()
                    img.src = canvas.toDataURL()
                    images.push(img)
                }
                return images
            },

            generateFallbackImages() {
                const totalNeeded = this.petals * this.subLayers
                this.images = this.generateTextureImages(totalNeeded)
                console.log(`✅ 生成 ${totalNeeded} 张彩色纹理图片作为后备`)
            },

            // 增强图片饱和度与亮度
            enhanceImage(img) {
                const canvas = document.createElement('canvas')
                canvas.width = img.width
                canvas.height = img.height
                const ctx = canvas.getContext('2d')
                ctx.filter = 'saturate(1.6) brightness(1.1)'
                ctx.drawImage(img, 0, 0)
                ctx.filter = 'none'
                const enhanced = new Image()
                enhanced.src = canvas.toDataURL()
                return enhanced
            },

            // ---------- Canvas 尺寸自适应 ----------
            resizeCanvas() {
                const w = window.innerWidth
                const h = window.innerHeight
                const maxDpr = w < 600 ? 1.5 : 2
                const dpr = Math.min(window.devicePixelRatio || 1, maxDpr)

                this.canvas.width = w * dpr
                this.canvas.height = h * dpr
                this.canvas.style.width = w + 'px'
                this.canvas.style.height = h + 'px'

                const ctx = this.ctx
                ctx.setTransform(1, 0, 0, 1, 0, 0)
                ctx.scale(dpr, dpr)

                this.cx = w / 2
                this.cy = h / 2
                this.maxR = Math.max(w, h) * 1.02
                this.angleStep = (Math.PI * 2) / this.petals

                // 暗角
                this.vignetteGrad = ctx.createRadialGradient(
                    this.cx, this.cy, this.maxR * 0.3,
                    this.cx, this.cy, this.maxR
                )
                this.vignetteGrad.addColorStop(0, 'rgba(0,0,0,0)')
                this.vignetteGrad.addColorStop(0.6, 'rgba(0,0,0,0)')
                this.vignetteGrad.addColorStop(1, 'rgba(0,0,0,0.12)')

                const centerR = this.maxR * 0.015
                this.centerGrad = ctx.createRadialGradient(
                    this.cx, this.cy, 0,
                    this.cx, this.cy, centerR
                )
                this.centerGrad.addColorStop(0, 'rgba(255,255,255,0.05)')
                this.centerGrad.addColorStop(1, 'rgba(0,0,0,0.1)')
            },

            // ---------- 核心绘制 ----------
            drawKaleidoscope(timestamp) {
                if (timestamp - this.lastFrameTime < this.fpsInterval) {
                    this.animationId = requestAnimationFrame((t) => this.drawKaleidoscope(t))
                    return
                }
                this.lastFrameTime = timestamp

                const ctx = this.ctx
                const w = window.innerWidth
                const h = window.innerHeight
                const { cx, cy, maxR, angleStep, petals, subLayers, images } = this

                // 动态背景
                const bgHue = (this.angle * 30) % 360
                const bgGrad = ctx.createLinearGradient(0, 0, w, h)
                bgGrad.addColorStop(0, `hsl(${bgHue}, 70%, 30%)`)
                bgGrad.addColorStop(0.5, `hsl(${(bgHue + 60) % 360}, 70%, 25%)`)
                bgGrad.addColorStop(1, `hsl(${(bgHue + 120) % 360}, 70%, 30%)`)
                ctx.fillStyle = bgGrad
                ctx.fillRect(0, 0, w, h)

                this.angle += 0.0015
                const imgCount = images.length

                // 即使 images 为空，也绘制彩色碎片（使用纯色）
                const hasImages = imgCount > 0

                // 绘制碎片
                for (let i = 0; i < petals; i++) {
                    const midA = i * angleStep + this.angle
                    for (let s = 0; s < subLayers; s++) {
                        const r1 = maxR * (s / subLayers)
                        const r2 = maxR * ((s + 1) / subLayers)
                        const a1 = midA - angleStep * 0.51
                        const a2 = midA + angleStep * 0.51

                        const getXY = (r, a) => ({
                            x: cx + r * Math.cos(a),
                            y: cy + r * Math.sin(a),
                        })
                        const v1 = getXY(r1, a1)
                        const v2 = getXY(r2, a1)
                        const v3 = getXY(r2, a2)
                        const v4 = getXY(r1, a2)

                        ctx.save()
                        ctx.beginPath()
                        ctx.moveTo(v1.x, v1.y)
                        ctx.lineTo(v2.x, v2.y)
                        ctx.lineTo(v3.x, v3.y)
                        ctx.lineTo(v4.x, v4.y)
                        ctx.closePath()
                        ctx.clip()

                        if (hasImages) {
                            // 使用图片
                            const offset = Math.floor(this.angle * 2) % imgCount
                            const imgIdx = ((i * subLayers + s) + offset) % imgCount
                            const img = images[imgIdx]
                            if (img && img.complete && img.naturalWidth > 0) {
                                const aspect = img.width / img.height
                                const coverSize = r2 * 3.2
                                let dw = coverSize
                                let dh = coverSize / aspect
                                if (dh < coverSize) {
                                    dh = coverSize
                                    dw = dh * aspect
                                }
                                const centerR = (r1 + r2) / 2
                                const cxOff = centerR * 0.45 * Math.cos(midA)
                                const cyOff = centerR * 0.45 * Math.sin(midA)
                                const mx = cx + cxOff + r2 * 0.03 * Math.sin(i * 1.3 + s * 0.8 + this.angle * 0.4)
                                const my = cy + cyOff + r2 * 0.03 * Math.cos(i * 1.4 + s * 0.9 + this.angle * 0.5)

                                ctx.save()
                                ctx.translate(mx, my)
                                ctx.rotate(this.angle * 0.08 + i * 0.12 + s * 0.06)
                                ctx.drawImage(img, -dw / 2, -dh / 2, dw, dh)
                                ctx.restore()

                                // 光晕叠加
                                ctx.save()
                                ctx.translate(mx, my)
                                ctx.rotate(this.angle * 0.08 + i * 0.12 + s * 0.06)
                                ctx.globalCompositeOperation = 'overlay'
                                const hue1 = (i * 25 + this.angle * 40) % 360
                                const hue2 = (i * 25 + s * 30 + this.angle * 30) % 360
                                const grad = ctx.createLinearGradient(-dw / 2, -dh / 2, dw / 2, dh / 2)
                                grad.addColorStop(0, `hsla(${hue1}, 90%, 60%, 0.25)`)
                                grad.addColorStop(0.5, `hsla(${(hue1 + 60) % 360}, 90%, 60%, 0.15)`)
                                grad.addColorStop(1, `hsla(${hue2}, 90%, 60%, 0.25)`)
                                ctx.fillStyle = grad
                                ctx.fillRect(-dw / 2, -dh / 2, dw, dh)
                                ctx.restore()
                            } else {
                                // 图片无效时用纯色
                                const hue = (i * 25 + s * 40 + this.angle * 15) % 360
                                ctx.fillStyle = `hsl(${hue}, 80%, 50%)`
                                ctx.fillRect(0, 0, w, h)
                            }
                        } else {
                            // 无图片时用纯色
                            const hue = (i * 25 + s * 40 + this.angle * 15) % 360
                            ctx.fillStyle = `hsl(${hue}, 80%, 50%)`
                            ctx.fillRect(0, 0, w, h)
                        }
                        ctx.restore()
                    }
                }

                // 网格线（径向）
                for (let i = 0; i < petals; i++) {
                    const a = i * angleStep + this.angle
                    const hue = (i * 20 + this.angle * 30) % 360
                    ctx.save()
                    const lineGrad = ctx.createLinearGradient(
                        cx, cy,
                        cx + maxR * Math.cos(a),
                        cy + maxR * Math.sin(a)
                    )
                    lineGrad.addColorStop(0, `hsla(${hue}, 100%, 80%, 0.9)`)
                    lineGrad.addColorStop(1, `hsla(${(hue + 60) % 360}, 100%, 70%, 0)`)
                    ctx.beginPath()
                    ctx.moveTo(cx, cy)
                    ctx.lineTo(cx + maxR * Math.cos(a), cy + maxR * Math.sin(a))
                    ctx.strokeStyle = lineGrad
                    ctx.lineWidth = 1.5
                    ctx.stroke()
                    ctx.restore()
                }

                // 环向网格
                for (let s = 1; s <= subLayers; s++) {
                    const r = maxR * (s / subLayers)
                    const hue = (s * 40 + this.angle * 20) % 360
                    ctx.save()
                    ctx.beginPath()
                    for (let i = 0; i <= petals; i++) {
                        const a = i * angleStep + this.angle
                        const x = cx + r * Math.cos(a)
                        const y = cy + r * Math.sin(a)
                        if (i === 0) ctx.moveTo(x, y)
                        else ctx.lineTo(x, y)
                    }
                    ctx.strokeStyle = `hsla(${hue}, 100%, 80%, 0.8)`
                    ctx.lineWidth = 1.2
                    ctx.stroke()
                    ctx.restore()
                }

                // 暗角
                ctx.fillStyle = this.vignetteGrad
                ctx.fillRect(0, 0, w, h)

                // 中心装饰
                ctx.fillStyle = this.centerGrad
                ctx.beginPath()
                ctx.arc(cx, cy, maxR * 0.015, 0, Math.PI * 2)
                ctx.fill()

                this.animationId = requestAnimationFrame((t) => this.drawKaleidoscope(t))
            },

            // ---------- 登录方法 ----------
            async onLogin() {
                if (!this.loginForm.username || !this.loginForm.username.trim()) {
                    showToast('请输入用户名或手机号')
                    return
                }
                if (!this.loginForm.password || this.loginForm.password.length < 6) {
                    showToast('密码至少6位')
                    return
                }
                this.loginLoading = true
                try {
                    const store = useUserStore()
                    await store.login({
                        username: this.loginForm.username.trim(),
                        password: this.loginForm.password,
                        end: 'tourist'
                    })
                    await store.getUserInfo()
                    const role = store.userInfo?.role || 'user'
                    if (role === 'admin') {
                        // 管理员账号只能在管理端登录，游客端拒绝
                        store.logout()
                        showToast('管理员账号请在管理端登录')
                        this.loginForm.password = ''
                        return
                    }
                    showToast('登录成功')
                    this.$router.replace('/home')
                } catch (e) {
                    showToast(e.msg || e.message || '登录失败')
                } finally {
                    this.loginLoading = false
                }
            },

            // ---------- 注册方法 ----------
            async onRegister() {
                const username = this.registerForm.username.trim()
                const password = this.registerForm.password
                const nickname = this.registerForm.nickname.trim() || username
                const phone = this.registerForm.phone.trim()

                if (!username || username.length < 4 || username.length > 20) {
                    showToast('用户名4-20位字母或数字')
                    return
                }
                if (!/^[a-zA-Z0-9_]+$/.test(username)) {
                    showToast('用户名只能包含字母、数字、下划线')
                    return
                }
                if (!password || password.length < 6) {
                    showToast('密码至少6位')
                    return
                }
                if (phone && !isValidPhone(phone)) {
                    showToast('请输入正确的手机号')
                    return
                }

                this.registerLoading = true
                try {
                    await registerApi({ username, password, nickname, phone, role: 'user' })
                    showToast('注册成功，请登录')
                    this.loginForm.username = username
                    this.isLoginMode = true
                    this.registerForm = { username: '', password: '', nickname: '', phone: '' }
                } catch (e) {
                    showToast(e.msg || e.message || '注册失败')
                } finally {
                    this.registerLoading = false
                }
            },
        },
    }
</script>

<style scoped>
    .login-wrapper {
        position: relative;
        width: 100vw;
        height: 100vh;
        overflow: hidden;
        background: #0a0a0a;
    }
    .kaleidoscope-bg {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        z-index: 0;
    }
    .login-box {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: 420px;
        max-width: 92%;
        padding: 40px 30px;
        background: rgba(10, 10, 30, 0.25);
        backdrop-filter: blur(16px) saturate(1.3);
        -webkit-backdrop-filter: blur(16px) saturate(1.3);
        border-radius: 32px;
        border: 1px solid rgba(255, 255, 255, 0.08);
        box-shadow: 0 8px 50px rgba(0, 0, 0, 0.4);
        color: #fff;
        text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
        z-index: 10;
    }
    .login-box h2 {
        text-align: center;
        font-size: 28px;
        font-weight: 700;
        margin-bottom: 24px;
        letter-spacing: 3px;
        background: linear-gradient(135deg, #f0e6d0, #ffd700, #f0e6d0);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
    }
    .tab-switch {
        display: flex;
        justify-content: center;
        gap: 40px;
        margin-bottom: 24px;
    }
    .tab-switch span {
        font-size: 18px;
        font-weight: 500;
        color: rgba(255, 255, 255, 0.5);
        cursor: pointer;
        padding-bottom: 4px;
        transition: all 0.3s;
    }
    .tab-switch span.active {
        color: #fff;
        border-bottom: 2px solid #ffd700;
    }
    .login-box :deep(.van-field) {
        background: rgba(255, 255, 255, 0.05);
        border-radius: 14px;
        margin-bottom: 14px;
        border: 1px solid rgba(255, 255, 255, 0.06);
    }
    .login-box :deep(.van-field:focus-within) {
        border-color: rgba(255, 215, 0, 0.2);
    }
    .login-box :deep(.van-field__label) {
        color: rgba(255, 255, 255, 0.8);
    }
    .login-box :deep(.van-field__control) {
        color: #fff;
    }
    .login-box :deep(.van-field__control::placeholder) {
        color: rgba(255, 255, 255, 0.3);
    }
    .login-box :deep(.van-button) {
        background: rgba(255, 255, 255, 0.06);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #fff;
        font-weight: 500;
        border-radius: 14px;
    }
    .login-box :deep(.van-button--primary) {
        background: linear-gradient(135deg, rgba(102, 126, 234, 0.4), rgba(118, 75, 162, 0.4));
    }
    .switch-hint {
        text-align: center;
        font-size: 14px;
        color: rgba(255, 255, 255, 0.7);
    }
    .switch-hint span {
        color: #fff;
        cursor: pointer;
        text-decoration: underline;
    }
    @media (max-width: 480px) {
        .login-box {
            padding: 28px 18px;
            width: 95%;
        }
        .login-box h2 {
            font-size: 22px;
        }
        .tab-switch span {
            font-size: 16px;
        }
    }
</style>