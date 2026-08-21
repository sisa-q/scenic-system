// ============================================================
// threeScene.js — 故宫数字孪生 · 高精化三维可视化（数据场沉浸式决策沙盘）
//
// 对应《故宫三维化可视化技术高精化》技术方案的 WebGL 落地实现：
//   1) 超写实几何建模与 LOD 分级渲染
//      - 三级 HLOD（近景高模 / 中景中模 / 远景实例化网格 InstancedMesh）
//      - 建筑模数依据《清式营造则例》柱径高比约 1:10 参数化（示意）
//      - PBR 材质（漫反射/粗糙度/金属度，Web 端以程序化参数近似 8K 贴图）
//   2) 动态数据场与流体粒子算法
//      - Marching Squares 等值线：双线性插值 + 32 级密度梯度 + D3 冰蓝→深红色阶
//      - 边缘羽化量随相机俯仰角自适应
//      - 半透明监测立柱：滑动 IQR 异常检测 + 菲涅尔环形光晕（冷白→琥珀 2Hz 脉冲）
//        + 空间遮罩"压力气泡"
//      - 彩色粒子动线：速度场（主轴层流 + 密度梯度扩散 + 湍流旋度）+ 四维向量
//        （位置/速度/生命期/颜色）+ CIE1931 标定色带（绿/青/橙黄/赤红）
//        + 加色混合指数衰减光流尾迹 + 空间哈希斥力约束（模拟 CUDA 原子操作）
//      - 双缓冲乒乓交换：本实现以 JS 双数组模拟 GPU 双缓冲；WebGPU 路径可直接
//        替换为 Compute Shader 解算
//   3) 沉浸渲染管线
//      - ACES Filmic 色调映射 + SSAA 超采样抗锯齿（TAA 时域方案的静态等价实现，
//        亦可切换为 TAARenderPass 真时域累计）+ UnrealBloom 泛光
//      - 自动曝光适配（Eye Adaptation）：按太阳轨迹平滑调节曝光
//      - 阴影：4096 级联阴影的 Web 端降级（2048 PCF 软阴影）
//      - OIT：WebGL 下以深度排序 + 加色混合近似；WebGPU 路径可升级为
//        Per-Pixel Linked List
//   4) 数据同步与孪生时延控制
//      - Delta Sync 差分同步：WebSocket 优先（毫秒级全双工），模拟器兜底
//      - 单帧网络负载实时统计；Time Slicing 将重任务分摊到多帧
//      - 核心算法以 WebAssembly 移植为后续优化项（注释标注）
// ============================================================
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { SSAARenderPass } from 'three/examples/jsm/postprocessing/SSAARenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { TimeController, GUGONG_LAYOUT, HOTSPOTS, SimulationEngine, SIM_CONFIG } from './dataSimulator.js'
import { initCrowdInteraction } from './crowdInteraction.js'

// ============================================================
// 常量配置
// ============================================================
const CONFIG = {
    quality: 'high',            // high: SSAA 4 采样 ; low: SSAA 2 采样
    bloomStrength: 0.45,
    bloomRadius: 0.5,
    bloomThreshold: 0.82,
    heatOpacity: 0.42,          // 热力面基础透明度（随俯仰角自适应）
    contourOpacity: 0.55,       // 等值线基础透明度（随俯仰角自适应）
    particleCount: 3200,
    trailHistory: 4,            // 光流尾迹历史缓冲层数（指数衰减）
    speedToMs: 8.0,             // 场景速度 → 真实 m/s 标定系数（CIE1931 色带映射）
    columnCountX: 7,
    columnCountZ: 6,
    columnRingSize: 240,        // IQR 滑动窗口长度（帧）
    contourLevels: 32,          // 32 级连续密度梯度
    contourStride: 3,           // 每 3 级绘制一条等值线（共约 11 条，兼顾帧率）
    lodNear: 5,                 // 近景阈值：高模
    lodMid: 10,                 // 中景阈值：中模
    fieldSize: 96,              // 热力网格 96×96（虚拟网格剖分）
    contourSize: 48,            // 等值线剖分网格 48×48
}

// 数据场覆盖范围（略大于城墙）
const FIELD = { xMin: -6.5, xMax: 6.5, zMin: -5.5, zMax: 8.5 }
const FIELD_W = FIELD.xMax - FIELD.xMin
const FIELD_H = FIELD.zMax - FIELD.zMin
const HOTSPOT_SIGMA2 = 2.4

// D3.js 风格 Ice Blue → Deep Red 多停靠点色阶
const GRADIENT_STOPS = [
    { t: 0.0,  c: [0.13, 0.59, 0.95] }, // 冰蓝
    { t: 0.25, c: [0.10, 0.85, 0.85] }, // 青
    { t: 0.5,  c: [0.30, 0.90, 0.45] }, // 绿
    { t: 0.7,  c: [1.00, 0.85, 0.20] }, // 黄
    { t: 0.85, c: [1.00, 0.55, 0.15] }, // 橙
    { t: 1.0,  c: [1.00, 0.10, 0.10] }, // 深红
]

// 建筑模数参数（柱径:柱高 ≈ 1:10 示意，重檐/翼角等通过程序化参数生成）
const BUILDING_DIMS = {
    taihe:    { w: 1.6, d: 0.9, h: 1.0, doubleEave: true, grand: true },
    zhonghe:  { w: 0.9, d: 0.6, h: 0.65 },
    baohe:    { w: 1.1, d: 0.65, h: 0.7 },
    qianqing: { w: 1.3, d: 0.75, h: 0.85, doubleEave: true },
    jiaotai:  { w: 0.7, d: 0.5, h: 0.55 },
    kunning:  { w: 1.3, d: 0.75, h: 0.85, doubleEave: true },
    wumen:    { w: 1.6, d: 0.6, h: 0.8, grand: true },
    shenwu:   { w: 1.6, d: 0.6, h: 0.8, grand: true },
    wenhua:   { w: 0.9, d: 0.55, h: 0.65 },
    wuying:   { w: 0.9, d: 0.55, h: 0.65 },
    fengxian: { w: 0.8, d: 0.5, h: 0.6 },
    fengxian2:{ w: 0.8, d: 0.5, h: 0.6 },
    yuhua:    { w: 0.7, d: 0.7, h: 0.5 },
    entrance: { w: 0.9, d: 0.4, h: 0.5 },
    exit:     { w: 0.9, d: 0.4, h: 0.5 },
}
const DEFAULT_DIM = { w: 0.7, d: 0.5, h: 0.6 }

// 材质色板（PBR 程序化参数近似）
const MAT_COLORS = {
    wall: 0xcc3333,
    roof: 0xd4a017,
    pillar: 0x8B0000,
    base: 0x8B7355,
    gold: 0xffdd44,
}

// Marching Squares 查找表：case(0..15) → 相交边对（E0 下 / E1 右 / E2 上 / E3 左）
const MS_CASES = [
    [],            // 0
    [[0, 3]],      // 1
    [[0, 1]],      // 2
    [[1, 3]],      // 3
    [[1, 2]],      // 4
    [[0, 1], [2, 3]], // 5 鞍点
    [[0, 2]],      // 6
    [[2, 3]],      // 7
    [[2, 3]],      // 8
    [[0, 2]],      // 9
    [[0, 3], [1, 2]], // 10 鞍点
    [[1, 2]],      // 11
    [[1, 3]],      // 12
    [[0, 1]],      // 13
    [[0, 3]],      // 14
    [],            // 15
]

// ============================================================
// 模块级状态
// ============================================================
let scene, camera, renderer, labelRenderer, controls, composer, ssaaPass, bloomPass
let container = null
let isInitialized = false
let animationId = null
let timeController = null
let lastFrameTime = 0
let frameCounter = 0

// 数据场
let field = null          // 96×96 热力网格
let fieldC = null         // 48×48 等值线网格
let vMin = 0, vMax = 100, vSpan = 100
let heatGeo = null, heatColors = null, heatMesh = null
let contourGeo = null
const MAX_CONTOUR_VERTS = 50000
const contourPositions = new Float32Array(MAX_CONTOUR_VERTS * 3)
const contourColors = new Float32Array(MAX_CONTOUR_VERTS * 3)
let contourVCount = 0

// 建筑 / LOD
let buildings = []
let crowdCtl = null
let lowInstances = null
let lowRoofInstances = null
let lodCounts = { high: 0, mid: 0, low: 0 }

// 监测立柱
let columns = []
let columnInstances = null
let bubbles = []

// 粒子
let pPos = null, pVel = null, pLife = null, pColor = null, pHist = null
let ptsGeo = null, trailGeo = null
let flowWeights = {}

// 灯光 / 曝光
let sunLight = null, hemiLight = null, fillLight = null
let exposure = 1.0

// 同步 / HUD
let deltaSync = null
let hud = null

// 客流模拟引擎 / 时间进度条 / 统计面板 / 粒子曲线箭头
let simEngine = null
let timeBar = null
let timeSlider = null
let timeLabel = null
let timePlayBtn = null
let timeSpeedBtn = null
let statsPanel = null
let statInpark = null
let timeDragging = false
let speedFactor = 1
const UP_VEC = new THREE.Vector3(0, 1, 0)
let densityLabels = []
let arrowMesh = null
const ARROW_EVERY = 4

// 临时对象（避免每帧 GC 分配）
const tmpColor = new THREE.Color()
const tmpColor2 = new THREE.Color()
const tmpColor3 = new THREE.Color()
const tmpVec = new THREE.Vector3()
const tmpMtx = new THREE.Matrix4()
const tmpQuat = new THREE.Quaternion()
const tmpScale = new THREE.Vector3()
const tmpPos = new THREE.Vector3()

// ============================================================
// 通用工具
// ============================================================
// D3.js 风格多停靠点线性插值色阶
function flowColor(t, out) {
    const tc = Math.max(0, Math.min(1, t))
    let i = 0
    while (i < GRADIENT_STOPS.length - 2 && tc > GRADIENT_STOPS[i + 1].t) i++
    const a = GRADIENT_STOPS[i]
    const b = GRADIENT_STOPS[i + 1]
    const k = (tc - a.t) / Math.max(1e-6, b.t - a.t)
    out.r = a.c[0] + (b.c[0] - a.c[0]) * k
    out.g = a.c[1] + (b.c[1] - a.c[1]) * k
    out.b = a.c[2] + (b.c[2] - a.c[2]) * k
    return out
}

// 圆点纹理（粒子柔和光斑）
function makeCircleTexture() {
    const size = 64
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    const grad = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
    grad.addColorStop(0, 'rgba(255,255,255,1)')
    grad.addColorStop(0.4, 'rgba(255,255,255,0.6)')
    grad.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, size, size)
    const tex = new THREE.CanvasTexture(canvas)
    tex.needsUpdate = true
    return tex
}

// 时段客流系数（与 dataSimulator 一致，去掉随机抖动以保持数据场平滑）
function getTimeFactor(hour) {
    // 全天双峰客流曲线：8 时开园爬升，9-11 上午高峰，11-14 午间回落，
    // 14-16 下午高峰，16 时后闭园缓降
    if (hour <= 8) return 0.05
    if (hour <= 9) return 0.05 + 0.70 * (hour - 8)
    if (hour <= 11) return 0.75 + 0.25 * (hour - 9) / 2
    if (hour <= 14) return 1.0 - 0.4 * (hour - 11) / 3
    if (hour <= 16) return 0.6 + 0.35 * (hour - 14) / 2
    return 0.95 - 0.85 * (hour - 16) / 2
}

function smoothHotspotFlow(h, hour) {
    const tf = getTimeFactor(hour)
    const peakBonus = h.peakHours.includes(Math.floor(hour)) ? 0.5 : 0
    return Math.max(0, h.baseFlow * (tf + peakBonus * 0.3) + Math.sin(hour * 1.7 + h.x) * 4)
}

// 重建密度场（双数据源：热点高斯场 + 中轴通道人流 + 平滑时段扰动）
function buildDensityField(nx, nz, hour) {
    const f = new Float32Array(nx * nz)
    const flows = {}
    for (let k = 0; k < HOTSPOTS.length; k++) {
        const h = HOTSPOTS[k]
        const w = flowWeights[h.id] !== undefined ? flowWeights[h.id] : 1
        flows[h.id] = smoothHotspotFlow(h, hour) * w
    }
    for (let j = 0; j < nz; j++) {
        const wz = FIELD.zMin + (j / (nz - 1)) * FIELD_H
        for (let i = 0; i < nx; i++) {
            const wx = FIELD.xMin + (i / (nx - 1)) * FIELD_W
            let v = 0
            for (let k = 0; k < HOTSPOTS.length; k++) {
                const h = HOTSPOTS[k]
                const dx = wx - h.x
                const dz = wz - h.z
                v += flows[h.id] * Math.exp(-(dx * dx + dz * dz) / (2 * HOTSPOT_SIGMA2))
            }
            // 中轴线通道基础人流（南→北主路）
            v += 6 * Math.exp(-(wx * wx) / (2 * 1.6)) * (0.6 + 0.4 * Math.sin(hour * 1.1 + 0.5))
            // 时段迁移的平滑扰动（模拟客流缓慢漂移）
            v *= 0.9 + 0.1 * Math.sin(wx * 0.7 + hour * 1.3) * Math.cos(wz * 0.6 - hour * 0.8)
            f[j * nx + i] = Math.max(0, v)
        }
    }
    return f
}

// 双线性插值采样
function sampleField(x, z) {
    if (!field) return 0
    const nx = CONFIG.fieldSize
    const fx = (x - FIELD.xMin) / FIELD_W * (nx - 1)
    const fz = (z - FIELD.zMin) / FIELD_H * (nx - 1)
    const i0 = Math.floor(fx)
    const j0 = Math.floor(fz)
    if (i0 < 0 || j0 < 0 || i0 >= nx - 1 || j0 >= nx - 1) return 0
    const tx = fx - i0
    const tz = fz - j0
    const f00 = field[j0 * nx + i0]
    const f10 = field[j0 * nx + i0 + 1]
    const f01 = field[(j0 + 1) * nx + i0]
    const f11 = field[(j0 + 1) * nx + i0 + 1]
    return (f00 * (1 - tx) + f10 * tx) * (1 - tz) + (f01 * (1 - tx) + f11 * tx) * tz
}

// ============================================================
// 热力面（32 级密度梯度色带网格）
// ============================================================
function createHeatmap() {
    heatGeo = new THREE.PlaneGeometry(FIELD_W, FIELD_H, CONFIG.fieldSize - 1, CONFIG.fieldSize - 1)
    heatGeo.rotateX(-Math.PI / 2)
    heatColors = new Float32Array(heatGeo.attributes.position.count * 3)
    heatGeo.setAttribute('color', new THREE.BufferAttribute(heatColors, 3))
    heatMesh = new THREE.Mesh(heatGeo, new THREE.MeshBasicMaterial({
        vertexColors: true,
        transparent: true,
        opacity: CONFIG.heatOpacity,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide,
    }))
    heatMesh.position.y = 0.012
    heatMesh.renderOrder = 1
    scene.add(heatMesh)
}

function updateHeatmap() {
    const posAttr = heatGeo.attributes.position
    const vCount = posAttr.count
    for (let vi = 0; vi < vCount; vi++) {
        const wx = posAttr.getX(vi)
        const wz = posAttr.getZ(vi)
        const v = sampleField(wx, wz)
        const t = vSpan > 1e-6 ? (v - vMin) / vSpan : 0
        // 32 级密度梯度离散化
        const tq = Math.round(Math.max(0, Math.min(1, t)) * (CONFIG.contourLevels - 1)) / (CONFIG.contourLevels - 1)
        flowColor(tq, tmpColor)
        heatColors[vi * 3] = tmpColor.r
        heatColors[vi * 3 + 1] = tmpColor.g
        heatColors[vi * 3 + 2] = tmpColor.b
    }
    heatGeo.attributes.color.needsUpdate = true
}

// ============================================================
// Marching Squares 等值线（双线性插值 + 32 级梯度抽样）
// ============================================================
function createContourLines() {
    contourGeo = new THREE.BufferGeometry()
    contourGeo.setAttribute('position', new THREE.BufferAttribute(contourPositions, 3).setUsage(THREE.DynamicDrawUsage))
    contourGeo.setAttribute('color', new THREE.BufferAttribute(contourColors, 3).setUsage(THREE.DynamicDrawUsage))
    contourGeo.setDrawRange(0, 0)
    contourMat = new THREE.LineBasicMaterial({
        vertexColors: true,
        transparent: true,
        opacity: CONFIG.contourOpacity,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
    })
    const lines = new THREE.LineSegments(contourGeo, contourMat)
    lines.frustumCulled = false
    lines.renderOrder = 2
    scene.add(lines)
}

function emitContour(x1, z1, x2, z2, r, g, b) {
    if (contourVCount >= MAX_CONTOUR_VERTS - 2) return
    const o = contourVCount * 3
    contourPositions[o] = x1
    contourPositions[o + 1] = 0.025
    contourPositions[o + 2] = z1
    contourPositions[o + 3] = x2
    contourPositions[o + 4] = 0.025
    contourPositions[o + 5] = z2
    contourColors[o] = r
    contourColors[o + 1] = g
    contourColors[o + 2] = b
    contourColors[o + 3] = r
    contourColors[o + 4] = g
    contourColors[o + 5] = b
    contourVCount += 2
}

function rebuildContours() {
    contourVCount = 0
    const n = CONFIG.contourSize
    const stepX = FIELD_W / (n - 1)
    const stepZ = FIELD_H / (n - 1)
    const total = CONFIG.contourLevels
    for (let li = 0; li < total; li += CONFIG.contourStride) {
        const L = vMin + (li / (total - 1)) * vSpan
        const tq = li / (total - 1)
        flowColor(tq, tmpColor)
        const r = tmpColor.r, g = tmpColor.g, b = tmpColor.b
        for (let j = 0; j < n - 1; j++) {
            const z0 = FIELD.zMin + j * stepZ
            const z1 = z0 + stepZ
            for (let i = 0; i < n - 1; i++) {
                const x0 = FIELD.xMin + i * stepX
                const x1 = x0 + stepX
                const f00 = fieldC[j * n + i]
                const f10 = fieldC[j * n + i + 1]
                const f11 = fieldC[(j + 1) * n + i + 1]
                const f01 = fieldC[(j + 1) * n + i]
                const b00 = f00 >= L ? 1 : 0
                const b10 = f10 >= L ? 1 : 0
                const b11 = f11 >= L ? 1 : 0
                const b01 = f01 >= L ? 1 : 0
                const cs = b00 | (b10 << 1) | (b11 << 2) | (b01 << 3)
                if (cs === 0 || cs === 15) continue
                // 双线性插值边缘交点参数
                const e0 = b00 !== b10 ? (L - f00) / (f10 - f00) : -1
                const e1 = b10 !== b11 ? (L - f10) / (f11 - f10) : -1
                const e2 = b11 !== b01 ? (L - f11) / (f01 - f11) : -1
                const e3 = b01 !== b00 ? (L - f01) / (f00 - f01) : -1
                const segs = MS_CASES[cs]
                for (let s = 0; s < segs.length; s++) {
                    const ea = segs[s][0]
                    const eb = segs[s][1]
                    let ax, az, bx, bz
                    if (ea === 0) { ax = x0 + e0 * stepX; az = z0 }
                    else if (ea === 1) { ax = x1; az = z0 + e1 * stepZ }
                    else if (ea === 2) { ax = x1 - e2 * stepX; az = z1 }
                    else { ax = x0; az = z1 - e3 * stepZ }
                    if (eb === 0) { bx = x0 + e0 * stepX; bz = z0 }
                    else if (eb === 1) { bx = x1; bz = z0 + e1 * stepZ }
                    else if (eb === 2) { bx = x1 - e2 * stepX; bz = z1 }
                    else { bx = x0; bz = z1 - e3 * stepZ }
                    emitContour(ax, az, bx, bz, r, g, b)
                }
            }
        }
    }
    contourGeo.setDrawRange(0, contourVCount)
    contourGeo.attributes.position.needsUpdate = true
    contourGeo.attributes.color.needsUpdate = true
}

// ============================================================
// 灯光（球谐/半球近似 + 太阳轨迹 + 级联阴影降级）
// ============================================================
function initLights() {
    // HemisphereLight 即一阶球谐（SH）光照近似：天空/地面双半球
    hemiLight = new THREE.HemisphereLight(0x88aaff, 0x221122, 0.55)
    scene.add(hemiLight)

    sunLight = new THREE.DirectionalLight(0xfff2dd, 2.2)
    sunLight.castShadow = true
    sunLight.shadow.mapSize.set(2048, 2048)
    sunLight.shadow.camera.near = 0.5
    sunLight.shadow.camera.far = 30
    sunLight.shadow.camera.left = -8
    sunLight.shadow.camera.right = 8
    sunLight.shadow.camera.top = 8
    sunLight.shadow.camera.bottom = -8
    sunLight.shadow.bias = -0.0005
    scene.add(sunLight)
    scene.add(sunLight.target)

    fillLight = new THREE.DirectionalLight(0x4488ff, 0.35)
    fillLight.position.set(-10, 5, -10)
    scene.add(fillLight)
}

function updateLighting(hour, dt) {
    const t = (hour - 8) / 11 // 8:00~19:00 → 0..1
    const az = THREE.MathUtils.lerp(0.5, Math.PI - 0.5, t)
    const elev = Math.sin(t * Math.PI) * 1.0 + 0.18
    tmpVec.set(Math.cos(az) * Math.cos(elev), Math.sin(elev), Math.sin(az) * Math.cos(elev)).normalize()
    sunLight.position.copy(tmpVec).multiplyScalar(12)
    sunLight.target.position.set(0, 0, 0)
    // 日光色温：晨暖 → 午白 → 昏橙
    tmpColor.set(0xffd9a0)
    tmpColor2.set(0xfff6e8)
    tmpColor3.set(0xff9e5e)
    if (t < 0.5) tmpColor.copy(tmpColor).lerp(tmpColor2, t * 2)
    else tmpColor.copy(tmpColor2).lerp(tmpColor3, (t - 0.5) * 2)
    sunLight.color.copy(tmpColor)
    sunLight.intensity = 0.8 + 1.8 * Math.sin(t * Math.PI)
    // 自动曝光（Eye Adaptation）：随太阳轨迹平滑过渡
    const targetExposure = 0.7 + 0.8 * Math.sin(t * Math.PI)
    exposure += (targetExposure - exposure) * Math.min(1, dt * 2)
    renderer.toneMappingExposure = exposure
}

// ============================================================
// 地面 / 网格
// ============================================================
function createGround() {
    const ground = new THREE.Mesh(
        new THREE.PlaneGeometry(18, 18),
        new THREE.MeshStandardMaterial({ color: 0x12162a, roughness: 0.85, metalness: 0.05 })
    )
    ground.rotation.x = -Math.PI / 2
    ground.position.y = -0.02
    ground.receiveShadow = true
    scene.add(ground)

    const grid = new THREE.GridHelper(16, 24, 0x2a3a5a, 0x1b2a4a)
    grid.position.y = 0.001
    grid.material.transparent = true
    grid.material.opacity = 0.45
    grid.material.depthWrite = false
    scene.add(grid)
}

// ============================================================
// 初始化入口
// ============================================================
export function initScene(containerElement) {
    if (isInitialized) return
    container = containerElement
    const width = container.clientWidth || 800
    const height = container.clientHeight || 500

    renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' })
    renderer.setSize(width, height)
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFShadowMap
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.0
    renderer.outputColorSpace = THREE.SRGBColorSpace
    container.appendChild(renderer.domElement)

    scene = new THREE.Scene()
    scene.background = new THREE.Color(0x0a0a2a)
    scene.fog = new THREE.Fog(0x0a0a2a, 22, 36)

    camera = new THREE.PerspectiveCamera(42, width / height, 0.1, 100)
    camera.position.set(11, 7, 13)
    camera.lookAt(0, 0.4, 0)

    labelRenderer = new CSS2DRenderer()
    labelRenderer.setSize(width, height)
    labelRenderer.domElement.style.position = 'absolute'
    labelRenderer.domElement.style.top = '0'
    labelRenderer.domElement.style.left = '0'
    labelRenderer.domElement.style.pointerEvents = 'none'
    container.appendChild(labelRenderer.domElement)

    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.target.set(0, 0.4, 0)
    controls.minDistance = 3
    controls.maxDistance = 30
    controls.maxPolarAngle = Math.PI / 2.15
    controls.update()

    // 后处理链：SSAA（TAA 静态等价）→ Bloom → Output（ACES + sRGB）
    composer = new EffectComposer(renderer)
    composer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    composer.setSize(width, height)
    ssaaPass = new SSAARenderPass(scene, camera, 0x0a0a2a, 0)
    ssaaPass.sampleLevel = CONFIG.quality === 'high' ? 2 : 1
    composer.addPass(ssaaPass)
    bloomPass = new UnrealBloomPass(new THREE.Vector2(width, height), CONFIG.bloomStrength, CONFIG.bloomRadius, CONFIG.bloomThreshold)
    composer.addPass(bloomPass)
    composer.addPass(new OutputPass())

    initLights()
    createGround()
    createHeatmap()
    createContourLines()
    createGugongBuildings()
    createCityWall()
    createMonitoringColumns()
    initParticles()
    initArrows()
    initDeltaSync()

    simEngine = new SimulationEngine()
    lastFrameTime = performance.now()
    timeController = new TimeController(SIM_CONFIG.openHour, (SIM_CONFIG.closeHour - SIM_CONFIG.openHour) / SIM_CONFIG.playSeconds)
    timeController.onTick((hour) => {
        const now = performance.now()
        const dt = Math.min(0.05, (now - lastFrameTime) / 1000)
        lastFrameTime = now
        updateScene(hour, dt)
    })
    timeController.start()
    updateScene(8, 0.016)

    createHUD()
    createLegend()
    createControlButtons()
    createTimeBar()
    createStatsPanel()
    createDensityLabels()

    crowdCtl = initCrowdInteraction({
        container: container,
        renderer: renderer,
        camera: camera,
        scene: scene,
        getBuildings: () => buildings,
        getHour: () => timeController ? timeController.getCurrentHour() : 12,
    })
    isInitialized = true
    animate()
}// ============================================================
// 故宫建筑群 · 三级 HLOD 程序化建模
// ============================================================
function stdMat(color, roughness, metalness) {
    return new THREE.MeshStandardMaterial({ color, roughness, metalness, side: THREE.DoubleSide })
}

function buildHighLOD(dim) {
    // 近景高模：台基 + 墙身 + 立柱 + 檐椽 + 翼角 + 重檐/庑殿顶 + 正脊 + 宝顶
    const g = new THREE.Group()
    const w = dim.w, d = dim.d, h = dim.h
    const wallH = h * 0.46

    const base = new THREE.Mesh(new THREE.BoxGeometry(w + 0.16, 0.06, d + 0.16), stdMat(MAT_COLORS.base, 0.7, 0.05))
    base.position.y = 0.03
    base.receiveShadow = true
    g.add(base)

    const wall = new THREE.Mesh(new THREE.BoxGeometry(w, wallH, d), stdMat(MAT_COLORS.wall, 0.85, 0.0))
    wall.position.y = 0.06 + wallH / 2
    wall.castShadow = true
    wall.receiveShadow = true
    g.add(wall)

    // 立柱（柱径:柱高 ≈ 1:10）
    const pillarMat = stdMat(MAT_COLORS.pillar, 0.5, 0.1)
    const px = [-w / 2 + 0.08, w / 2 - 0.08]
    const pz = [-d / 2 + 0.08, d / 2 - 0.08]
    px.forEach((dx) => {
        pz.forEach((dz) => {
            const pillar = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.028, wallH * 0.82, 6), pillarMat)
            pillar.position.set(dx, 0.06 + wallH * 0.41, dz)
            pillar.castShadow = true
            g.add(pillar)
        })
    })

    // 檐椽（出挑）
    const eave = new THREE.Mesh(new THREE.BoxGeometry(w + 0.24, 0.03, d + 0.24), stdMat(MAT_COLORS.roof, 0.45, 0.25))
    eave.position.y = 0.06 + wallH + 0.015
    eave.castShadow = true
    g.add(eave)

    // 翼角（四角出挑，示意翼角椽飞）
    if (dim.grand) {
        const wingMat = stdMat(MAT_COLORS.roof, 0.45, 0.3)
        const cornerPos = [[-1, -1], [1, -1], [1, 1], [-1, 1]]
        cornerPos.forEach(([sx, sz]) => {
            const wing = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.025, 0.05), wingMat)
            wing.position.set(sx * (w / 2 + 0.12), 0.06 + wallH + 0.032, sz * (d / 2 + 0.12))
            wing.rotation.y = Math.PI / 4 * sx * sz
            g.add(wing)
        })
    }

    // 下层屋顶（庑殿顶以四棱锥近似）
    const roofR = Math.max(w, d) * 0.62
    const roofH = h * 0.22
    const roof = new THREE.Mesh(new THREE.ConeGeometry(roofR, roofH, 4), stdMat(MAT_COLORS.roof, 0.4, 0.35))
    roof.position.y = 0.06 + wallH + 0.03 + roofH / 2 + 0.01
    roof.rotation.y = Math.PI / 4
    roof.castShadow = true
    g.add(roof)

    let topY = roof.position.y + roofH / 2
    if (dim.doubleEave) {
        // 重檐上层
        const roof2R = roofR * 0.55
        const roof2H = h * 0.16
        const roof2 = new THREE.Mesh(new THREE.ConeGeometry(roof2R, roof2H, 4), stdMat(MAT_COLORS.roof, 0.4, 0.35))
        roof2.position.y = roof.position.y + roofH / 2 + roof2H / 2 + 0.01
        roof2.rotation.y = Math.PI / 4
        roof2.castShadow = true
        g.add(roof2)
        topY = roof2.position.y + roof2H / 2
    }

    // 正脊 + 宝顶
    const ridge = new THREE.Mesh(new THREE.BoxGeometry(Math.max(w, d) * 0.45, 0.02, 0.03), stdMat(MAT_COLORS.roof, 0.4, 0.3))
    ridge.position.y = topY - 0.01
    g.add(ridge)

    const top = new THREE.Mesh(new THREE.SphereGeometry(0.035, 8, 8), new THREE.MeshStandardMaterial({
        color: MAT_COLORS.gold, roughness: 0.25, metalness: 0.6, emissive: 0xff8800, emissiveIntensity: 0.25,
    }))
    top.position.y = topY + 0.01
    g.add(top)

    return g
}

function buildMidLOD(dim) {
    // 中景中模：减面优化
    const g = new THREE.Group()
    const w = dim.w, d = dim.d, h = dim.h
    const wallH = h * 0.5
    const base = new THREE.Mesh(new THREE.BoxGeometry(w + 0.12, 0.05, d + 0.12), stdMat(MAT_COLORS.base, 0.7, 0.05))
    base.position.y = 0.025
    g.add(base)
    const wall = new THREE.Mesh(new THREE.BoxGeometry(w, wallH, d), stdMat(MAT_COLORS.wall, 0.85, 0.0))
    wall.position.y = 0.05 + wallH / 2
    wall.castShadow = true
    g.add(wall)
    const roofR = Math.max(w, d) * 0.58
    const roofH = h * 0.3
    const roof = new THREE.Mesh(new THREE.ConeGeometry(roofR, roofH, 4), stdMat(MAT_COLORS.roof, 0.4, 0.3))
    roof.position.y = 0.05 + wallH + roofH / 2 + 0.01
    roof.rotation.y = Math.PI / 4
    roof.castShadow = true
    g.add(roof)
    const ridge = new THREE.Mesh(new THREE.BoxGeometry(Math.max(w, d) * 0.4, 0.02, 0.03), stdMat(MAT_COLORS.roof, 0.4, 0.3))
    ridge.position.y = 0.05 + wallH + roofH - 0.01
    g.add(ridge)
    return g
}

function createGugongBuildings() {
    // 远景：GPU 硬件实例化（InstancedMesh，单 Draw Call 绘制全部远景建筑）
    const lowGeo = new THREE.BoxGeometry(1, 1, 1)
    const lowMat = new THREE.MeshStandardMaterial({ color: 0x994433, roughness: 0.8, metalness: 0.05 })
    const ids = Object.keys(GUGONG_LAYOUT)
    lowInstances = new THREE.InstancedMesh(lowGeo, lowMat, ids.length)
    lowInstances.frustumCulled = false
    const lowRoofGeo = new THREE.ConeGeometry(0.8, 0.55, 4)
    const lowRoofMat = new THREE.MeshStandardMaterial({ color: MAT_COLORS.roof, roughness: 0.5, metalness: 0.3, side: THREE.DoubleSide })
    lowRoofInstances = new THREE.InstancedMesh(lowRoofGeo, lowRoofMat, ids.length)
    lowRoofInstances.frustumCulled = false

    ids.forEach((id, index) => {
        const b = GUGONG_LAYOUT[id]
        const dim = BUILDING_DIMS[id] || DEFAULT_DIM
        const root = new THREE.Group()
        root.position.set(b.x, 0, b.z)

        const high = buildHighLOD(dim)
        const mid = buildMidLOD(dim)
        mid.visible = false
        root.add(high)
        root.add(mid)

        // 名称标签
        const labelDiv = document.createElement('div')
        labelDiv.textContent = b.name
        labelDiv.style.color = '#ffdd99'
        labelDiv.style.fontSize = '12px'
        labelDiv.style.fontWeight = 'bold'
        labelDiv.style.textShadow = '0 0 10px rgba(0,0,0,0.9), 0 0 20px rgba(0,0,0,0.7)'
        labelDiv.style.fontFamily = '"SimSun", serif'
        labelDiv.style.letterSpacing = '1px'
        labelDiv.style.padding = '2px 8px'
        labelDiv.style.background = 'rgba(0,0,0,0.3)'
        labelDiv.style.borderRadius = '4px'
        labelDiv.style.border = '1px solid rgba(255,200,100,0.2)'
        const label = new CSS2DObject(labelDiv)
        label.position.set(0, dim.h + 0.25, 0)
        root.add(label)

        scene.add(root)
        buildings.push({ id: b.id, x: b.x, z: b.z, dim, root, high, mid, index, level: -1 })

        // 实例矩阵初始隐藏（scale 0）
        tmpPos.set(b.x, 0.15, b.z)
        tmpQuat.identity()
        tmpScale.set(0, 0, 0)
        tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
        lowInstances.setMatrixAt(index, tmpMtx)
        lowRoofInstances.setMatrixAt(index, tmpMtx)
        lowInstances.setColorAt(index, new THREE.Color(0xcc8855))
    })
    lowInstances.instanceMatrix.needsUpdate = true
    lowInstances.instanceColor.needsUpdate = true
    scene.add(lowInstances)
    lowRoofInstances.instanceMatrix.needsUpdate = true
    scene.add(lowRoofInstances)

    // 中轴线地砖（示意）
    const lineMat = new THREE.LineBasicMaterial({ color: 0x444466, transparent: true, opacity: 0.2 })
    for (let z = -4; z <= 7; z += 0.5) {
        const pts = [new THREE.Vector3(-0.02, 0.01, z), new THREE.Vector3(0.02, 0.01, z)]
        const geo = new THREE.BufferGeometry().setFromPoints(pts)
        scene.add(new THREE.Line(geo, lineMat))
    }
}

function updateLOD() {
    const camPos = camera.position
    lodCounts = { high: 0, mid: 0, low: 0 }
    buildings.forEach((b) => {
        const dist = Math.hypot(camPos.x - b.x, camPos.z - b.z)
        const level = dist < CONFIG.lodNear ? 0 : (dist < CONFIG.lodMid ? 1 : 2)
        if (level !== b.level) {
            b.level = level
            b.high.visible = level === 0
            b.mid.visible = level === 1
            tmpPos.set(b.x, 0.15, b.z)
            tmpQuat.identity()
            if (level === 2) {
                const dim = b.dim
                tmpScale.set(dim.w * 0.85, dim.h * 0.72, dim.d * 0.85)
            } else {
                tmpScale.set(0, 0, 0)
            }
            tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
            lowInstances.setMatrixAt(b.index, tmpMtx)
            lowInstances.instanceMatrix.needsUpdate = true
            if (level === 2) {
                const dim = b.dim
                const boxH = dim.h * 0.72
                tmpPos.set(b.x, 0.15 + boxH / 2 + boxH * 0.32, b.z)
                tmpScale.set(dim.w * 0.85 * 0.9, boxH * 0.6, dim.d * 0.85 * 0.9)
            } else {
                tmpPos.set(b.x, 0.15, b.z)
                tmpScale.set(0, 0, 0)
            }
            tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
            lowRoofInstances.setMatrixAt(b.index, tmpMtx)
            lowRoofInstances.instanceMatrix.needsUpdate = true
        }
        lodCounts[level === 0 ? 'high' : (level === 1 ? 'mid' : 'low')]++
    })
}

// ============================================================
// 城墙（含四角角楼）
// ============================================================
function createCityWall() {
    const wallMat = new THREE.MeshStandardMaterial({ color: 0x8B4513, roughness: 0.9, metalness: 0.0 })
    const xMin = -5.5, xMax = 5.5, zMin = -5, zMax = 7.5
    const wallH = 0.3, thickness = 0.06

    function addSegment(x1, z1, x2, z2) {
        const dx = x2 - x1, dz = z2 - z1
        const len = Math.sqrt(dx * dx + dz * dz)
        const seg = new THREE.Mesh(new THREE.BoxGeometry(len, wallH, thickness), wallMat)
        seg.position.set((x1 + x2) / 2, wallH / 2, (z1 + z2) / 2)
        seg.rotation.y = Math.atan2(dz, dx)
        seg.castShadow = true
        seg.receiveShadow = true
        scene.add(seg)
    }
    // 南门（午门）留口
    addSegment(xMin, zMin, -1.2, zMin)
    addSegment(1.2, zMin, xMax, zMin)
    // 北门（神武门）留口
    addSegment(xMin, zMax, -1.2, zMax)
    addSegment(1.2, zMax, xMax, zMax)
    // 东西墙
    addSegment(xMin, zMin, xMin, zMax)
    addSegment(xMax, zMin, xMax, zMax)

    // 四角角楼
    const towerMat = new THREE.MeshStandardMaterial({ color: 0x8B4513, roughness: 0.8 })
    ;[[xMin, zMin], [xMax, zMin], [xMax, zMax], [xMin, zMax]].forEach(([tx, tz]) => {
        const tower = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.45, 0.28), towerMat)
        tower.position.set(tx, 0.225, tz)
        tower.castShadow = true
        scene.add(tower)
        const roof = new THREE.Mesh(new THREE.ConeGeometry(0.24, 0.18, 4), stdMat(MAT_COLORS.roof, 0.4, 0.3))
        roof.position.set(tx, 0.45 + 0.09, tz)
        roof.rotation.y = Math.PI / 4
        scene.add(roof)
    })
}

// ============================================================
// 半透明监测立柱（场域监测节点）
//   - 滑动 IQR 异常检测：瞬时密度 > max(基线×1.5, 基线+1.5×IQR) 触发告警
//   - 菲涅尔环形光晕：冷白(6500K) → 琥珀(2700K) 2Hz 脉冲
//   - 空间遮罩"压力气泡"
// ============================================================
const HALO_VS = `
varying vec3 vNormal;
varying vec3 vViewDir;
void main() {
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vNormal = normalize(normalMatrix * normal);
    vViewDir = normalize(cameraPosition - wp.xyz);
    gl_Position = projectionMatrix * viewMatrix * wp;
}`

const HALO_FS = `
uniform vec3 uColor;
uniform float uIntensity;
uniform float uPower;
varying vec3 vNormal;
varying vec3 vViewDir;
void main() {
    float f = pow(1.0 - max(dot(normalize(vNormal), normalize(vViewDir)), 0.0), uPower);
    gl_FragColor = vec4(uColor * f * uIntensity, f);
}`

function createMonitoringColumns() {
    const xs = []
    const colCountX = CONFIG.columnCountX
    for (let i = 0; i < colCountX; i++) xs.push(-4.5 + i * (9 / (colCountX - 1)))
    const zs = []
    const colCountZ = CONFIG.columnCountZ
    for (let j = 0; j < colCountZ; j++) zs.push(-3.5 + j * (10 / (colCountZ - 1)))

    const colGeo = new THREE.CylinderGeometry(0.045, 0.05, 1, 10, 1)
    colGeo.translate(0, 0.5, 0)
    const colMat = new THREE.MeshPhysicalMaterial({
        color: 0xffffff, transparent: true, opacity: 0.28, roughness: 0.15, metalness: 0.1,
        depthWrite: false, side: THREE.DoubleSide,
    })
    columnInstances = new THREE.InstancedMesh(colGeo, colMat, xs.length * zs.length)
    columnInstances.frustumCulled = false
    columnInstances.renderOrder = 3
    scene.add(columnInstances)

    const haloGeo = new THREE.SphereGeometry(0.075, 14, 14)
    const ringGeo = new THREE.TorusGeometry(0.085, 0.008, 8, 28)
    let idx = 0
    xs.forEach((x) => {
        zs.forEach((z) => {
            const haloMat = new THREE.ShaderMaterial({
                uniforms: {
                    uColor: { value: new THREE.Color(0xcfe8ff) },
                    uIntensity: { value: 0.35 },
                    uPower: { value: 2.6 },
                },
                vertexShader: HALO_VS,
                fragmentShader: HALO_FS,
                transparent: true,
                blending: THREE.AdditiveBlending,
                depthWrite: false,
            })
            const halo = new THREE.Mesh(haloGeo, haloMat)
            halo.position.set(x, 0.4, z)
            scene.add(halo)

            const ringMat = new THREE.MeshBasicMaterial({
                color: 0xcfe8ff, transparent: true, opacity: 0.5,
                blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide,
            })
            const ring = new THREE.Mesh(ringGeo, ringMat)
            ring.rotation.x = Math.PI / 2
            ring.position.set(x, 0.42, z)
            scene.add(ring)

            columns.push({
                x, z, idx,
                samples: [],
                baseline: 30,
                iqr: 8,
                alarm: false,
                alarmStart: 0,
                height: 0.4,
                haloMat,
                ringMat,
                halo,
                ring,
            })

            tmpPos.set(x, 0, z)
            tmpQuat.identity()
            tmpScale.set(1, 0.4, 1)
            tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
            columnInstances.setMatrixAt(idx, tmpMtx)
            columnInstances.setColorAt(idx, new THREE.Color(0xbfd8ff))
            idx++
        })
    })
    columnInstances.instanceMatrix.needsUpdate = true
    columnInstances.instanceColor.needsUpdate = true

    initBubbles()
}

function initBubbles() {
    const geo = new THREE.SphereGeometry(0.06, 12, 12)
    for (let i = 0; i < 12; i++) {
        const mat = new THREE.MeshBasicMaterial({
            color: 0xffb300, transparent: true, opacity: 0,
            blending: THREE.AdditiveBlending, depthWrite: false,
        })
        const m = new THREE.Mesh(geo, mat)
        m.visible = false
        scene.add(m)
        bubbles.push({ mesh: m, active: false, t: 0, x: 0, y: 0, z: 0 })
    }
}

function spawnBubble(c) {
    const b = bubbles.find((bb) => !bb.active)
    if (!b) return
    b.active = true
    b.t = 0
    b.x = c.x
    b.z = c.z
    b.y = c.height + 0.08
}

function updateBubbles(now, dt) {
    bubbles.forEach((b) => {
        if (!b.active) return
        b.t += dt
        const k = Math.min(1, b.t / 1.6)
        b.mesh.position.set(b.x, b.y + k * 0.6, b.z)
        b.mesh.scale.setScalar(0.5 + k * 2.5)
        b.mesh.material.opacity = 0.55 * (1 - k)
        b.mesh.visible = k < 1
        if (k >= 1) b.active = false
    })
}

function updateColumns(now, dt) {
    const white = tmpColor2.set(0xffffff)
    const amber = tmpColor3.set(0xffb300)
    columns.forEach((c) => {
        const d = sampleField(c.x, c.z)
        c.samples.push(d)
        if (c.samples.length > CONFIG.columnRingSize) c.samples.shift()
        c.height = 0.15 + (d / Math.max(1e-5, vMax)) * 0.8

        // IQR 异常检测（Time Slicing：每 3 帧重算一次统计量）
        if (frameCounter % 3 === 0 && c.samples.length >= 60) {
            const sorted = c.samples.slice().sort((a, b2) => a - b2)
            const len = sorted.length
            const q1 = sorted[Math.floor(len * 0.25)]
            const q3 = sorted[Math.floor(len * 0.75)]
            const iqr = q3 - q1
            const median = len % 2 ? sorted[len >> 1] : (sorted[len / 2 - 1] + sorted[len / 2]) / 2
            c.baseline = median
            c.iqr = iqr
            const thr = Math.max(median * 1.5, median + 1.5 * iqr)
            const anomaly = d > thr && d > 8
            if (anomaly && !c.alarm) {
                c.alarmStart = now
                spawnBubble(c)
            }
            // 告警保持 2.5s（滞回防抖）
            c.alarm = anomaly || (c.alarm && now - c.alarmStart < 2500)
        }

        // 菲涅尔光晕：冷白 → 琥珀，2Hz 脉冲
        const alarmF = c.alarm ? 1 : 0
        const pulse = c.alarm ? 0.5 + 0.5 * Math.sin(2 * Math.PI * 2 * (now / 1000)) : 0
        c.haloMat.uniforms.uColor.value.lerpColors(white, amber, alarmF)
        c.haloMat.uniforms.uIntensity.value = c.alarm ? 0.9 + 0.6 * pulse : 0.35
        c.haloMat.uniforms.uPower.value = c.alarm ? 1.8 : 2.6
        c.halo.position.set(c.x, c.height + 0.06, c.z)
        c.ring.position.set(c.x, c.height + 0.065, c.z)
        c.ringMat.color.lerpColors(white, amber, alarmF)
        c.ringMat.opacity = c.alarm ? 0.5 + 0.5 * pulse : 0.35

        tmpPos.set(c.x, 0, c.z)
        tmpQuat.identity()
        tmpScale.set(1, c.height, 1)
        tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
        columnInstances.setMatrixAt(c.idx, tmpMtx)
        columnInstances.setColorAt(c.idx, c.alarm ? amber : white)
    })
    columnInstances.instanceMatrix.needsUpdate = true
    columnInstances.instanceColor.needsUpdate = true
    updateBubbles(now, dt)
}

// ============================================================
// 彩色粒子动线系统
//   - 速度场：主轴层流 + 密度梯度扩散 + 湍流旋度噪声
//   - 双缓冲乒乓交换（JS 双数组模拟 GPU 双缓冲）
//   - CIE1931 标定色带：绿(畅行>1.2m/s)/青(正常0.6-1.2)/橙黄(缓行0.2-0.6)/赤红(停滞<0.2)
//   - 加色混合 + 指数衰减历史缓冲区 → 光流尾迹
//   - 空间哈希斥力约束（模拟 CUDA 原子操作，避免粒子堆叠结晶）
// ============================================================
const AXIS_PATH = [
    { x: 0, z: -4.6 }, { x: 0, z: -3.5 }, { x: 0, z: -1.5 }, { x: 0, z: 2.0 },
    { x: 0, z: 4.0 }, { x: 0, z: 6.5 }, { x: 0, z: 7.5 },
]
const SPURS = [
    [{ x: 0, z: -1.5 }, { x: -2.5, z: -1.5 }],
    [{ x: 0, z: -1.5 }, { x: 2.5, z: -1.5 }],
    [{ x: 0, z: 2.0 }, { x: -2.5, z: 1.5 }],
    [{ x: 0, z: 2.0 }, { x: 2.5, z: 1.5 }],
]
let PATH_SAMPLES = null

function buildPathSamples() {
    if (PATH_SAMPLES) return
    PATH_SAMPLES = []
    const axisSamples = []
    for (let k = 0; k < AXIS_PATH.length - 1; k++) {
        const a = AXIS_PATH[k], b = AXIS_PATH[k + 1]
        const dist = Math.hypot(b.x - a.x, b.z - a.z)
        const steps = Math.max(1, Math.ceil(dist / 0.2))
        for (let s = 0; s <= steps; s++) {
            const t = s / steps
            axisSamples.push({
                x: a.x + (b.x - a.x) * t,
                z: a.z + (b.z - a.z) * t,
                tx: (b.x - a.x) / dist,
                tz: (b.z - a.z) / dist,
                strength: 1,
            })
        }
    }
    PATH_SAMPLES = axisSamples.slice()
    SPURS.forEach(([a, b]) => {
        const dist = Math.hypot(b.x - a.x, b.z - a.z)
        const steps = Math.max(1, Math.ceil(dist / 0.25))
        for (let s = 0; s <= steps; s++) {
            const t = s / steps
            PATH_SAMPLES.push({
                x: a.x + (b.x - a.x) * t,
                z: a.z + (b.z - a.z) * t,
                tx: (b.x - a.x) / dist,
                tz: (b.z - a.z) / dist,
                strength: 0.45,
            })
        }
    })
}

// 密度 → 目标速度映射（畅行/正常/缓行/停滞，单位 m/s）
// 密度 <8 畅行(>1.2) / 8-25 正常(0.6-1.2) / 25-55 缓行(0.2-0.6) / >55 停滞(<0.2)
function congestionSpeed(density) {
    const pts = [
        [0, 1.5],
        [8, 1.2],
        [25, 0.6],
        [55, 0.2],
        [200, 0.05],
    ]
    const d = Math.max(0, density)
    for (let i = 0; i < pts.length - 1; i++) {
        const d0 = pts[i][0], s0 = pts[i][1]
        const d1 = pts[i + 1][0], s1 = pts[i + 1][1]
        if (d <= d1) return s0 + (s1 - s0) * (d - d0) / (d1 - d0)
    }
    return 0.05
}

// 速度场（拉格朗日视角）：方向来自 主轴层流 + 密度梯度扩散 + 湍流旋度，
// 速度大小由密度映射为目标速度，保证畅行/正常/缓行/停滞四态同时呈现
function velocityFieldAt(x, z, hour) {
    buildPathSamples()
    const density = sampleField(x, z)
    // 1) 主轴层流牵引（方向）
    let bestD2 = Infinity, btx = 0, btz = 1, bstr = 0
    for (let k = 0; k < PATH_SAMPLES.length; k++) {
        const p = PATH_SAMPLES[k]
        const dx = x - p.x, dz = z - p.z
        const d2 = dx * dx + dz * dz
        if (d2 < bestD2) {
            bestD2 = d2
            btx = p.tx
            btz = p.tz
            bstr = p.strength
        }
    }
    const pathStrength = Math.exp(-bestD2 / (2 * 1.1 * 1.1)) * bstr
    // 2) 密度梯度扩散（-∇f，从拥挤流向空旷）
    const EPS = 0.08
    const gx = (sampleField(x + EPS, z) - sampleField(x - EPS, z)) / (2 * EPS)
    const gz = (sampleField(x, z + EPS) - sampleField(x, z - EPS)) / (2 * EPS)
    const glen = Math.hypot(gx, gz) || 0
    // 3) 湍流旋度噪声（层流与湍流交织）
    const t = hour * 1.0
    const swx = Math.sin(x * 1.2 + t * 0.7) * Math.cos(z * 0.9 - t * 0.5)
    const swz = Math.cos(x * 0.8 - t * 0.6) * Math.sin(z * 1.3 + t * 0.4)
    // 合成方向（层流牵引为主，辅以梯度扩散与旋度湍流）
    let dx = btx * pathStrength * 0.7 - (glen > 1e-4 ? (gx / glen) * 0.4 : 0) + swx * 0.25
    let dz = btz * pathStrength * 0.7 - (glen > 1e-4 ? (gz / glen) * 0.4 : 0) + swz * 0.25
    const dl = Math.hypot(dx, dz) || 1
    dx /= dl
    dz /= dl
    // 速度大小：密度 → 目标速度（四态同时呈现）
    const spd = congestionSpeed(density)
    return { vx: dx * spd, vz: dz * spd }
}

function speedColor(speedMs, out) {
    // CIE1931 标定色带
    if (speedMs >= 1.2) out.setRGB(0.20, 1.00, 0.35)     // 畅行 绿
    else if (speedMs >= 0.6) out.setRGB(0.10, 0.90, 0.95) // 正常 青
    else if (speedMs >= 0.2) out.setRGB(1.00, 0.62, 0.15) // 缓行 橙黄
    else out.setRGB(1.00, 0.15, 0.12)                    // 停滞 赤红
    return out
}

function respawnParticle(i) {
    pPos[i * 3] = (Math.random() - 0.5) * 0.4
    pPos[i * 3 + 1] = 0.15
    pPos[i * 3 + 2] = -4.6 + (Math.random() - 0.5) * 0.2
    pVel[i * 2] = (Math.random() - 0.5) * 0.02
    pVel[i * 2 + 1] = 0.05 + Math.random() * 0.05
    pLife[i] = 25 + Math.random() * 50
}

// 空间哈希斥力约束（模拟 CUDA 原子操作：桶内成对斥力）
let cellHead = null, cellNext = null
function applyRepulsion() {
    const cellSize = 0.3
    const gWidth = Math.ceil(FIELD_W / cellSize) + 2
    const gHeight = Math.ceil(FIELD_H / cellSize) + 2
    const gx0 = Math.floor(FIELD.xMin / cellSize)
    const gz0 = Math.floor(FIELD.zMin / cellSize)
    if (!cellHead) cellHead = new Int32Array(gWidth * gHeight)
    if (!cellNext) cellNext = new Int32Array(CONFIG.particleCount)
    cellHead.fill(-1)
    const n = CONFIG.particleCount
    for (let i = 0; i < n; i++) {
        const cx = Math.floor(pPos[i * 3] / cellSize) - gx0
        const cz = Math.floor(pPos[i * 3 + 2] / cellSize) - gz0
        if (cx < 0 || cz < 0 || cx >= gWidth || cz >= gHeight) {
            cellNext[i] = -1
            continue
        }
        const c = cz * gWidth + cx
        cellNext[i] = cellHead[c]
        cellHead[c] = i
    }
    const R2 = 0.12 * 0.12
    for (let i = 0; i < n; i++) {
        const cx = Math.floor(pPos[i * 3] / cellSize) - gx0
        const cz = Math.floor(pPos[i * 3 + 2] / cellSize) - gz0
        if (cx < 0 || cz < 0 || cx >= gWidth || cz >= gHeight) continue
        const c = cz * gWidth + cx
        let j = cellHead[c]
        let checks = 0
        while (j !== -1 && checks < 24) {
            if (j > i) {
                const dx = pPos[i * 3] - pPos[j * 3]
                const dz = pPos[i * 3 + 2] - pPos[j * 3 + 2]
                const d2 = dx * dx + dz * dz
                if (d2 > 1e-8 && d2 < R2) {
                    const d = Math.sqrt(d2)
                    const push = (R2 - d2) * 0.25
                    const ux = dx / d, uz = dz / d
                    pPos[i * 3] += ux * push
                    pPos[i * 3 + 2] += uz * push
                    pPos[j * 3] -= ux * push
                    pPos[j * 3 + 2] -= uz * push
                }
            }
            j = cellNext[j]
            checks++
        }
    }
}

function initParticles() {
    const n = CONFIG.particleCount
    pPos = new Float32Array(n * 3)
    pVel = new Float32Array(n * 2)
    pLife = new Float32Array(n)
    pColor = new Float32Array(n * 3)
    pHist = new Float32Array(n * CONFIG.trailHistory * 3)
    for (let i = 0; i < n; i++) respawnParticle(i)

    ptsGeo = new THREE.BufferGeometry()
    ptsGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3).setUsage(THREE.DynamicDrawUsage))
    ptsGeo.setAttribute('color', new THREE.BufferAttribute(pColor, 3).setUsage(THREE.DynamicDrawUsage))
    const ptsMat = new THREE.PointsMaterial({
        size: 0.075,
        map: makeCircleTexture(),
        vertexColors: true,
        transparent: true,
        opacity: 0.95,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        sizeAttenuation: true,
    })
    ptsMesh = new THREE.Points(ptsGeo, ptsMat)
    ptsMesh.frustumCulled = false
    ptsMesh.renderOrder = 4
    scene.add(ptsMesh)

    // 光流尾迹（加色混合 LineSegments）
    const segCount = (CONFIG.trailHistory - 1) * n
    const trailPos = new Float32Array(segCount * 2 * 3)
    const trailCol = new Float32Array(segCount * 2 * 3)
    trailGeo = new THREE.BufferGeometry()
    trailGeo.setAttribute('position', new THREE.BufferAttribute(trailPos, 3).setUsage(THREE.DynamicDrawUsage))
    trailGeo.setAttribute('color', new THREE.BufferAttribute(trailCol, 3).setUsage(THREE.DynamicDrawUsage))
    const trailMat = new THREE.LineBasicMaterial({
        vertexColors: true, transparent: true, opacity: 0.5,
        blending: THREE.AdditiveBlending, depthWrite: false,
    })
    trailMesh = new THREE.LineSegments(trailGeo, trailMat)
    trailMesh.frustumCulled = false
    trailMesh.renderOrder = 5
    scene.add(trailMesh)
}

function updateTrails() {
    const n = CONFIG.particleCount
    const hist = CONFIG.trailHistory
    // 历史位移：新位置写入 slot0，其余顺移（指数衰减历史缓冲区）
    for (let i = 0; i < n; i++) {
        const base = i * hist * 3
        for (let h = hist - 1; h >= 1; h--) {
            pHist[base + h * 3] = pHist[base + (h - 1) * 3]
            pHist[base + h * 3 + 1] = pHist[base + (h - 1) * 3 + 1]
            pHist[base + h * 3 + 2] = pHist[base + (h - 1) * 3 + 2]
        }
        pHist[base] = pPos[i * 3]
        pHist[base + 1] = pPos[i * 3 + 1]
        pHist[base + 2] = pPos[i * 3 + 2]
    }
    const tPos = trailGeo.attributes.position.array
    const tCol = trailGeo.attributes.color.array
    for (let i = 0; i < n; i++) {
        const base = i * hist * 3
        for (let s = 0; s < hist - 1; s++) {
            const o = (i * (hist - 1) + s) * 6
            const w = 0.55 - s * 0.15 // 头部亮、尾部暗 → 运动模糊质感
            tPos[o] = pHist[base + s * 3]
            tPos[o + 1] = pHist[base + s * 3 + 1]
            tPos[o + 2] = pHist[base + s * 3 + 2]
            tPos[o + 3] = pHist[base + (s + 1) * 3]
            tPos[o + 4] = pHist[base + (s + 1) * 3 + 1]
            tPos[o + 5] = pHist[base + (s + 1) * 3 + 2]
            const cr = pColor[i * 3] * w
            const cg = pColor[i * 3 + 1] * w
            const cb = pColor[i * 3 + 2] * w
            tCol[o] = cr; tCol[o + 1] = cg; tCol[o + 2] = cb
            tCol[o + 3] = cr; tCol[o + 4] = cg; tCol[o + 5] = cb
        }
    }
    trailGeo.attributes.position.needsUpdate = true
    trailGeo.attributes.color.needsUpdate = true
}

function updateParticles(dt) {
    const n = CONFIG.particleCount
    const hour = timeController.getCurrentHour()
    for (let i = 0; i < n; i++) {
        const x = pPos[i * 3]
        const z = pPos[i * 3 + 2]
        const f = velocityFieldAt(x, z, hour)
        // 速度平滑（向速度场收敛）
        pVel[i * 2] += (f.vx - pVel[i * 2]) * 0.06
        pVel[i * 2 + 1] += (f.vz - pVel[i * 2 + 1]) * 0.06
        pPos[i * 3] += pVel[i * 2] * dt
        pPos[i * 3 + 2] += pVel[i * 2 + 1] * dt
        pLife[i] -= dt
        if (pLife[i] <= 0 || pPos[i * 3 + 2] > 8.6 || pPos[i * 3 + 2] < -5.8 || Math.abs(pPos[i * 3]) > 6.8) {
            respawnParticle(i)
        }
        // 速度 → 色带
        const sp = Math.hypot(pVel[i * 2], pVel[i * 2 + 1])
        speedColor(sp, tmpColor)
        pColor[i * 3] = tmpColor.r
        pColor[i * 3 + 1] = tmpColor.g
        pColor[i * 3 + 2] = tmpColor.b
    }
    applyRepulsion()
    updateTrails()
    updateArrows()
    ptsGeo.attributes.position.needsUpdate = true
    ptsGeo.attributes.color.needsUpdate = true
}// ============================================================
// 主更新循环（Time Slicing：重任务分摊到多帧，防止主线程阻塞）
// ============================================================
let contourMat = null
let ptsMesh = null
let trailMesh = null
let hudButtons = null
let fpsValue = 60, fpsFrames = 0, fpsTime = 0, hudLast = 0

function updateAdaptiveOpacity() {
    // 边缘羽化量随相机俯仰角自适应（避免近景锯齿与远景模糊）
    if (!camera || !heatMesh || !contourMat) return
    const dir = camera.getWorldDirection(tmpVec)
    const elev = Math.max(0, dir.y)
    const fade = 0.55 + 0.45 * THREE.MathUtils.clamp(elev * 2.2, 0, 1)
    heatMesh.material.opacity = CONFIG.heatOpacity * fade
    contourMat.opacity = CONFIG.contourOpacity * fade
}

function updateScene(hour, dt) {
    frameCounter++
    // 重建双网格密度场（热力 + 等值线共用，重任务按帧分摊）
    field = buildDensityField(CONFIG.fieldSize, CONFIG.fieldSize, hour)
    fieldC = buildDensityField(CONFIG.contourSize, CONFIG.contourSize, hour)
    vMin = Infinity
    vMax = -Infinity
    for (let i = 0; i < field.length; i++) {
        const v = field[i]
        if (v < vMin) vMin = v
        if (v > vMax) vMax = v
    }
    vSpan = Math.max(1e-6, vMax - vMin)

    updateHeatmap()
    if (frameCounter % 2 === 0) rebuildContours()  // Time Slicing：等值线隔帧重建
    updateColumns(performance.now(), dt)
    updateParticles(dt)
    updateLighting(hour, dt)
    updateLOD()
    updateAdaptiveOpacity()
}

// ============================================================
// Delta Sync 差分同步（WebSocket 毫秒级全双工优先，模拟器兜底）
// ============================================================
let deltaSeq = 0

function buildSimulatedDelta() {
    const n = CONFIG.particleCount
    const ids = [], dx = [], dz = []
    const nudge = Math.min(40, n)
    for (let i = 0; i < nudge; i++) {
        const id = (Math.random() * n) | 0
        ids.push(id)
        dx.push((Math.random() - 0.5) * 0.02)
        dz.push((Math.random() - 0.5) * 0.02)
    }
    const hotspots = HOTSPOTS.slice(0, 3).map((h) => ({ id: h.id, delta: (Math.random() - 0.5) * 4 }))
    return { type: 'delta', seq: deltaSeq++, ts: Date.now(), particles: { ids, dx, dz }, hotspots }
}

function applyDelta(d) {
    if (!d || typeof d !== 'object') return
    if (Array.isArray(d.hotspots)) {
        d.hotspots.forEach((hs) => {
            if (hs && hs.id && flowWeights[hs.id] !== undefined) {
                flowWeights[hs.id] = Math.max(0.3, Math.min(3, flowWeights[hs.id] + (hs.delta || 0)))
            }
        })
    }
    if (d.particles && Array.isArray(d.particles.ids)) {
        const ids = d.particles.ids
        const dxArr = d.particles.dx || []
        const dzArr = d.particles.dz || []
        for (let k = 0; k < ids.length; k++) {
            const id = ids[k]
            if (id >= 0 && id < CONFIG.particleCount && pPos) {
                pPos[id * 3] += dxArr[k] || 0
                pPos[id * 3 + 2] += dzArr[k] || 0
            }
        }
    }
}

class DeltaSync {
    constructor(onDelta) {
        this.onDelta = onDelta
        this.ws = null
        // 生产：自动取当前站点同源 /ws/flow（Nginx 转发到后端）；开发环境走 dev proxy
        this.url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/flow'
        this.connected = false
        this.bytes = 0
        this.latency = 0
        this.simTimer = null
        this.pingTimer = null
        this._pingStart = 0
        this._reconnectTimer = null
        this._pageshowHandler = (e) => { if (e.persisted && !this.connected) this.reconnect() }
        window.addEventListener('pageshow', this._pageshowHandler)
    }
    connect() {
        try {
            this.ws = new WebSocket(this.url)
            this.ws.onopen = () => {
                this.connected = true
                this._stopSim()
                this._startPing()
            }
            this.ws.onmessage = (e) => {
                this.bytes = (e.data && e.data.length) || 0
                if (e.data === 'pong') {
                    this.latency = performance.now() - this._pingStart
                    return
                }
                try { this.onDelta(JSON.parse(e.data)) } catch (err) { /* 忽略非法帧 */ }
            }
            this.ws.onerror = () => { this.connected = false; this._startSim() }
            this.ws.onclose = () => { this.connected = false; this._startSim(); this._scheduleReconnect() }
            setTimeout(() => { if (!this.connected) this._startSim() }, 1500)
        } catch (err) {
            this._startSim()
        }
    }
    _startPing() {
        if (this.pingTimer) return
        this.pingTimer = setInterval(() => {
            if (this.ws && this.ws.readyState === 1) {
                this._pingStart = performance.now()
                this.ws.send('ping')
            }
        }, 2000)
    }
    _stopSim() {
        if (this.simTimer) { clearInterval(this.simTimer); this.simTimer = null }
    }
    _scheduleReconnect() {
        if (this._reconnectTimer) return
        this._reconnectTimer = setTimeout(() => { this._reconnectTimer = null; this.reconnect() }, 3000)
    }
    reconnect() {
        this._stopSim()
        this.connect()
    }
    _startSim() {
        if (this.simTimer) return
        this.simTimer = setInterval(() => {
            const delta = buildSimulatedDelta()
            this.bytes = JSON.stringify(delta).length
            this.latency = 3 + Math.random() * 12
            this.onDelta(delta)
        }, 600)
    }
    getLoadKB() { return this.bytes / 1024 }
    getLatencyMs() { return this.latency }
    dispose() {
        if (this._reconnectTimer) { clearTimeout(this._reconnectTimer); this._reconnectTimer = null }
        window.removeEventListener('pageshow', this._pageshowHandler)
        if (this.simTimer) clearInterval(this.simTimer)
        if (this.pingTimer) clearInterval(this.pingTimer)
        if (this.ws) { try { this.ws.close() } catch (err) { /* ignore */ } }
        this.ws = null
    }
}

function initDeltaSync() {
    HOTSPOTS.forEach((h) => { flowWeights[h.id] = 1 })
    deltaSync = new DeltaSync(applyDelta)
    deltaSync.connect()
}

// ============================================================
// HUD / 图例 / 控制按钮
// ============================================================
function createHUD() {
    hud = document.createElement('div')
    hud.id = 'gugong-hud'
    hud.style.cssText = 'position:absolute;top:10px;left:10px;z-index:60;color:#cfe8ff;font:11px/1.7 Consolas,Menlo,monospace;background:rgba(6,10,30,0.55);padding:8px 12px;border:1px solid rgba(120,180,255,0.25);border-radius:6px;pointer-events:none;white-space:pre;backdrop-filter:blur(3px);min-width:215px'
    container.appendChild(hud)
}

function formatHour(hour) {
    const hh = Math.floor(hour)
    const mm = Math.floor((hour - hh) * 60)
    return (hh < 10 ? '0' + hh : hh) + ':' + (mm < 10 ? '0' + mm : mm)
}

function updateHUD() {
    if (!hud || !renderer) return
    const now = performance.now()
    fpsFrames++
    if (now - fpsTime >= 500) {
        fpsValue = fpsFrames * 1000 / (now - fpsTime)
        fpsFrames = 0
        fpsTime = now
    }
    if (now - hudLast < 500) return
    hudLast = now
    const alarmCount = columns.filter((c) => c.alarm).length
    const hour = timeController ? timeController.getCurrentHour() : 8
    updateStatsPanel()
    updateDensityLabels()
    updateProgressBar()
    const inPark = simEngine ? simEngine.getStateAt(hour).inPark : 0
    hud.textContent = [
        '故宫数字孪生 · 高精化数据场',
        '帧率 ' + fpsValue.toFixed(0) + ' FPS | DrawCall ' + renderer.info.render.calls,
        '当前在园 ' + fmtNum(inPark) + ' 人 | 时刻 ' + formatHour(hour),
        '热力密度 ' + vMin.toFixed(1) + ' ~ ' + vMax.toFixed(1),
        '粒子 ' + CONFIG.particleCount + ' | 尾迹 ' + (CONFIG.trailHistory - 1) + ' 层',
        '等值线 ' + Math.ceil(CONFIG.contourLevels / CONFIG.contourStride) + ' 条 / ' + CONFIG.contourLevels + ' 级',
        '网格 ' + CONFIG.fieldSize + 'x' + CONFIG.fieldSize + ' | SSAA ' + Math.pow(2, ssaaPass.sampleLevel) + 'x',
        '监测节点 ' + columns.length + ' | 告警 ' + alarmCount,
        'DeltaSync ' + deltaSync.getLoadKB().toFixed(1) + ' KB/帧 | ' + deltaSync.getLatencyMs().toFixed(0) + 'ms',
        'LOD H' + lodCounts.high + ' M' + lodCounts.mid + ' L' + lodCounts.low + ' | 曝光 ' + renderer.toneMappingExposure.toFixed(2),
    ].join('\n')
}

function createLegend() {
    const box = document.createElement('div')
    box.className = 'gugong-legend'
    box.style.cssText = 'position:absolute;left:10px;bottom:18px;z-index:60;color:#cfe8ff;font:11px/1.6 Consolas,Menlo,monospace;background:rgba(6,10,30,0.5);padding:8px 12px;border:1px solid rgba(120,180,255,0.2);border-radius:6px;pointer-events:none'
    const bar = document.createElement('div')
    bar.style.cssText = 'width:172px;height:10px;border-radius:3px;background:linear-gradient(to right,#2195F2,#1AD9D9,#4DE673,#FFD933,#FF8C26,#FF1A1A)'
    const labels = document.createElement('div')
    labels.style.cssText = 'display:flex;justify-content:space-between;margin-top:4px;color:#9fb8d8'
    labels.innerHTML = '<span>畅行</span><span>正常</span><span>缓行</span><span>停滞</span>'
    const note = document.createElement('div')
    note.style.cssText = 'margin-top:4px;color:#7fa0c8'
    note.textContent = '冰蓝 → 深红 · D3 色阶（CIE1931 标定）'
    box.appendChild(bar)
    box.appendChild(labels)
    box.appendChild(note)
    container.appendChild(box)
}

function createControlButtons() {
    const wrap = document.createElement('div')
    wrap.className = 'gugong-btns'
    wrap.style.cssText = 'position:absolute;right:12px;bottom:18px;z-index:60;display:flex;gap:8px'

    const btnFull = document.createElement('button')
    btnFull.textContent = '⛶ 全屏'
    btnFull.onclick = () => {
        if (document.fullscreenElement) document.exitFullscreen()
        else if (container.requestFullscreen) container.requestFullscreen()
    }

    const btnQuality = document.createElement('button')
    btnQuality.textContent = '画质 高'
    btnQuality.onclick = () => setQuality(CONFIG.quality === 'high' ? 'low' : 'high')

    ;[btnFull, btnQuality].forEach((btn) => {
        btn.style.cssText = 'padding:5px 12px;background:rgba(10,16,38,0.7);color:#cfe8ff;border:1px solid rgba(120,180,255,0.35);border-radius:5px;cursor:pointer;font:12px/1.4 "Microsoft YaHei",sans-serif'
        wrap.appendChild(btn)
    })
    container.appendChild(wrap)
    hudButtons = { btnQuality }
}

// ============================================================
// 时间进度条 / 统计面板 / 密度数值标签 / 粒子曲线箭头
// ============================================================
function fmtNum(n) {
    return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function createTimeBar() {
    timeBar = document.createElement('div')
    timeBar.className = 'gugong-timebar'
    timeBar.style.cssText = 'position:absolute;left:50%;bottom:14px;transform:translateX(-50%);z-index:60;display:flex;align-items:center;gap:8px;background:rgba(6,10,30,0.65);border:1px solid rgba(120,180,255,0.3);border-radius:8px;padding:6px 12px;color:#cfe8ff;font:12px Consolas,Menlo,monospace;backdrop-filter:blur(3px)'

    timePlayBtn = document.createElement('button')
    timePlayBtn.textContent = '⏸'
    timePlayBtn.style.cssText = 'width:26px;height:26px;border-radius:50%;border:1px solid rgba(120,180,255,0.4);background:rgba(20,40,80,0.6);color:#cfe8ff;cursor:pointer;font-size:13px'
    timePlayBtn.onclick = () => {
        if (timeController.running) {
            timeController.stop()
            timePlayBtn.textContent = '▶'
        } else {
            timeController.start()
            timePlayBtn.textContent = '⏸'
        }
    }

    timeLabel = document.createElement('span')
    timeLabel.textContent = '08:00'
    timeLabel.style.cssText = 'min-width:44px;text-align:center;color:#7fe7ff'

    timeSlider = document.createElement('input')
    timeSlider.type = 'range'
    timeSlider.min = String(SIM_CONFIG.openHour)
    timeSlider.max = String(SIM_CONFIG.closeHour)
    timeSlider.step = '0.05'
    timeSlider.value = String(SIM_CONFIG.openHour)
    timeSlider.style.cssText = 'width:220px;accent-color:#4a9eff;cursor:pointer'
    timeSlider.addEventListener('pointerdown', () => { timeDragging = true })
    timeSlider.addEventListener('input', () => {
        const h = parseFloat(timeSlider.value)
        timeLabel.textContent = formatHour(h)
        setSimTime(h)
    })
    timeSlider.addEventListener('change', () => { timeDragging = false })

    const endLabel = document.createElement('span')
    endLabel.textContent = '18:00'
    endLabel.style.color = '#7fa0c8'

    timeSpeedBtn = document.createElement('button')
    timeSpeedBtn.textContent = '1x'
    timeSpeedBtn.style.cssText = 'padding:3px 10px;border:1px solid rgba(120,180,255,0.35);border-radius:5px;background:rgba(20,40,80,0.6);color:#cfe8ff;cursor:pointer;font:12px Consolas,monospace'
    timeSpeedBtn.onclick = () => setSpeedFactor(speedFactor === 1 ? 2 : (speedFactor === 2 ? 0.5 : 1))

    const hint = document.createElement('span')
    hint.textContent = '8时→18时 / 30秒'
    hint.style.color = '#6f8cb0'
    hint.style.fontSize = '11px'

    timeBar.appendChild(timePlayBtn)
    timeBar.appendChild(timeLabel)
    timeBar.appendChild(timeSlider)
    timeBar.appendChild(endLabel)
    timeBar.appendChild(timeSpeedBtn)
    timeBar.appendChild(hint)
    container.appendChild(timeBar)
}

function createStatsPanel() {
    statsPanel = document.createElement('div')
    statsPanel.className = 'gugong-stats'
    statsPanel.style.cssText = 'position:absolute;top:10px;right:10px;z-index:60;min-width:215px;color:#cfe8ff;font:12px/1.8 "Microsoft YaHei",sans-serif;background:rgba(6,10,30,0.6);border:1px solid rgba(120,180,255,0.28);border-radius:8px;padding:10px 12px;pointer-events:none;backdrop-filter:blur(3px)'

    const title = document.createElement('div')
    title.textContent = '客流统计 · 数据沙盘'
    title.style.cssText = 'font-weight:bold;color:#7fe7ff;margin-bottom:6px;border-bottom:1px solid rgba(120,180,255,0.2);padding-bottom:4px'

    const rowInpark = document.createElement('div')
    rowInpark.innerHTML = '当前在园 <b style="color:#4dffa6;font-size:18px" id="gg-inpark">0</b> 人'
    statInpark = rowInpark.querySelector('#gg-inpark')

    const summary = simEngine.getSummary()
    const row2 = document.createElement('div')
    row2.style.cssText = 'display:flex;justify-content:space-between;color:#9fb8d8;font-size:12px;margin-top:4px'
    row2.innerHTML = '<span>今日入园 ' + fmtNum(summary.totalEntries) + '</span>'
    const row3 = document.createElement('div')
    row3.style.cssText = 'display:flex;justify-content:space-between;color:#9fb8d8;font-size:12px'
    row3.innerHTML = '<span>今日订单 ' + fmtNum(summary.totalOrders) + '</span>'
    const row4 = document.createElement('div')
    row4.style.cssText = 'display:flex;justify-content:space-between;color:#9fb8d8;font-size:12px'
    row4.innerHTML = '<span>累计游客 ' + fmtNum(summary.cumulativeVisitors) + '</span>'
    const row5 = document.createElement('div')
    row5.style.cssText = 'display:flex;justify-content:space-between;color:#9fb8d8;font-size:12px'
    row5.innerHTML = '<span>峰值在园 ' + fmtNum(summary.peakInPark) + '</span>'

    const barsTitle = document.createElement('div')
    barsTitle.textContent = '各时段入园占比'
    barsTitle.style.cssText = 'margin-top:8px;color:#9fb8d8;border-top:1px solid rgba(120,180,255,0.2);padding-top:6px'

    const bars = document.createElement('div')
    bars.style.cssText = 'margin-top:4px'
    const props = summary.hourlyProportion
    const maxP = Math.max.apply(null, props) || 1
    for (let k = 0; k < props.length; k++) {
        const hrow = document.createElement('div')
        hrow.style.cssText = 'display:flex;align-items:center;gap:6px;height:15px;font-size:11px;color:#7fa0c8'
        const lab = document.createElement('span')
        lab.textContent = (SIM_CONFIG.openHour + k) + '-' + (SIM_CONFIG.openHour + k + 1)
        lab.style.cssText = 'width:32px;flex:none;text-align:right'
        const track = document.createElement('div')
        track.style.cssText = 'flex:1;height:8px;background:rgba(120,160,220,0.15);border-radius:4px;overflow:hidden'
        const fill = document.createElement('div')
        fill.style.cssText = 'height:100%;width:' + (props[k] / maxP * 100) + '%;background:linear-gradient(to right,#4a9eff,#4dffa6);border-radius:4px'
        track.appendChild(fill)
        const pct = document.createElement('span')
        pct.textContent = (props[k] * 100).toFixed(1) + '%'
        pct.style.cssText = 'width:46px;flex:none;color:#cfe8ff'
        hrow.appendChild(lab)
        hrow.appendChild(track)
        hrow.appendChild(pct)
        bars.appendChild(hrow)
    }

    statsPanel.appendChild(title)
    statsPanel.appendChild(rowInpark)
    statsPanel.appendChild(row2)
    statsPanel.appendChild(row3)
    statsPanel.appendChild(row4)
    statsPanel.appendChild(row5)
    statsPanel.appendChild(barsTitle)
    statsPanel.appendChild(bars)
    container.appendChild(statsPanel)
}

function createDensityLabels() {
    HOTSPOTS.forEach((h) => {
        const div = document.createElement('div')
        div.style.cssText = 'color:#7fe7ff;font:11px Consolas,Menlo,monospace;text-shadow:0 0 6px rgba(0,0,0,0.95);background:rgba(0,10,24,0.5);border:1px solid rgba(120,220,255,0.25);border-radius:4px;padding:1px 6px;letter-spacing:0.5px'
        const label = new CSS2DObject(div)
        label.position.set(h.x + 0.55, 1.18, h.z)
        scene.add(label)
        densityLabels.push({ h, label, div })
    })
}

function updateDensityLabels() {
    densityLabels.forEach((d) => {
        const v = sampleField(d.h.x, d.h.z)
        d.div.textContent = d.h.name + ' ' + v.toFixed(1)
    })
}

function initArrows() {
    const n = Math.ceil(CONFIG.particleCount / ARROW_EVERY)
    const geo = new THREE.ConeGeometry(0.035, 0.1, 6)
    const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false })
    arrowMesh = new THREE.InstancedMesh(geo, mat, n)
    arrowMesh.frustumCulled = false
    arrowMesh.renderOrder = 6
    scene.add(arrowMesh)
    tmpQuat.setFromUnitVectors(UP_VEC, tmpVec.set(0, 0, 1))
    for (let i = 0; i < n; i++) {
        tmpPos.set(0, -10, 0)
        tmpScale.set(1, 1, 1)
        tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
        arrowMesh.setMatrixAt(i, tmpMtx)
        arrowMesh.setColorAt(i, tmpColor.setRGB(1, 1, 1))
    }
    arrowMesh.instanceMatrix.needsUpdate = true
    arrowMesh.instanceColor.needsUpdate = true
}

function updateArrows() {
    if (!arrowMesh) return
    const n = arrowMesh.count
    for (let i = 0; i < n; i++) {
        const pi = i * ARROW_EVERY
        const vx = pVel[pi * 2]
        const vz = pVel[pi * 2 + 1]
        const len = Math.hypot(vx, vz)
        if (len < 1e-4) {
            tmpPos.set(0, -10, 0)
            tmpScale.set(0, 0, 0)
        } else {
            tmpVec.set(vx / len, 0, vz / len)
            tmpQuat.setFromUnitVectors(UP_VEC, tmpVec)
            tmpPos.set(pPos[pi * 3], 0.2, pPos[pi * 3 + 2])
            tmpScale.set(1, 1, 1)
        }
        tmpMtx.compose(tmpPos, tmpQuat, tmpScale)
        arrowMesh.setMatrixAt(i, tmpMtx)
        arrowMesh.setColorAt(i, tmpColor.setRGB(pColor[pi * 3], pColor[pi * 3 + 1], pColor[pi * 3 + 2]))
    }
    arrowMesh.instanceMatrix.needsUpdate = true
    arrowMesh.instanceColor.needsUpdate = true
}

function updateProgressBar() {
    if (!timeSlider || !timeLabel || !timeController) return
    const hour = timeController.getCurrentHour()
    if (!timeDragging) timeSlider.value = String(Number(hour.toFixed(2)))
    timeLabel.textContent = formatHour(hour)
    timePlayBtn.textContent = timeController.running ? '⏸' : '▶'
    timeSpeedBtn.textContent = speedFactor + 'x'
}

function updateStatsPanel() {
    if (!statInpark || !simEngine || !timeController) return
    const st = simEngine.getStateAt(timeController.getCurrentHour())
    statInpark.textContent = fmtNum(st.inPark)
}

// ============================================================
// 渲染循环
// ============================================================
function animate() {
    animationId = requestAnimationFrame(animate)
    if (crowdCtl) crowdCtl.update()
    controls.update()
    if (composer) composer.render()
    if (labelRenderer) labelRenderer.render(scene, camera)
    updateHUD()
}

// ============================================================
// 对外 API
// ============================================================
export function destroyScene() {
    if (crowdCtl) { crowdCtl.dispose(); crowdCtl = null }
    if (animationId) cancelAnimationFrame(animationId)
    if (timeController) { timeController.stop(); timeController = null }
    if (deltaSync) { deltaSync.dispose(); deltaSync = null }
    if (container) {
        container.querySelectorAll('#gugong-hud, .gugong-legend, .gugong-btns, .gugong-timebar, .gugong-stats').forEach((el) => el.remove())
    }
    densityLabels.forEach((d) => { if (scene) scene.remove(d.label) })
    densityLabels = []
    if (arrowMesh) {
        arrowMesh.dispose()
        if (scene) scene.remove(arrowMesh)
        arrowMesh = null
    }
    if (renderer) {
        renderer.dispose()
        if (container && renderer.domElement) container.removeChild(renderer.domElement)
    }
    if (labelRenderer && container) container.removeChild(labelRenderer.domElement)
    buildings.forEach((b) => { if (scene && b.root) scene.remove(b.root) })
    buildings = []
    if (lowInstances) { lowInstances.dispose(); if (scene) scene.remove(lowInstances); lowInstances = null }
    if (lowRoofInstances) { lowRoofInstances.dispose(); if (scene) scene.remove(lowRoofInstances); lowRoofInstances = null }
    if (columnInstances) { columnInstances.dispose(); if (scene) scene.remove(columnInstances); columnInstances = null }
    columns.forEach((c) => { if (scene) { scene.remove(c.halo); scene.remove(c.ring) } })
    columns = []
    bubbles.forEach((b) => { if (scene) scene.remove(b.mesh) })
    bubbles = []
    if (ptsMesh) {
        if (ptsMesh.material.map) ptsMesh.material.map.dispose()
        ptsMesh.geometry.dispose()
        if (scene) scene.remove(ptsMesh)
        ptsMesh = null
    }
    if (trailMesh) {
        trailMesh.geometry.dispose()
        if (scene) scene.remove(trailMesh)
        trailMesh = null
    }
    if (heatMesh && scene) scene.remove(heatMesh)
    if (composer) { composer.dispose(); composer = null }
    if (hud && hud.parentNode) hud.parentNode.removeChild(hud)
    hud = null
    timeBar = null
    timeSlider = null
    timeLabel = null
    timePlayBtn = null
    timeSpeedBtn = null
    statsPanel = null
    statInpark = null
    simEngine = null
    scene = null
    camera = null
    renderer = null
    controls = null
    container = null
    isInitialized = false
}

export function resizeScene() {
    if (!container || !camera || !renderer) return
    const width = container.clientWidth
    const height = container.clientHeight
    if (width > 0 && height > 0) {
        camera.aspect = width / height
        camera.updateProjectionMatrix()
        renderer.setSize(width, height)
        if (composer) {
            composer.setSize(width, height)
            composer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
        }
        if (labelRenderer) labelRenderer.setSize(width, height)
    }
}

export function setTimeSpeed(speed) {
    if (timeController && typeof speed === 'number' && speed > 0) {
        timeController.speed = speed
    }
}

// 播放速度倍率（1x = 全天 30 秒）
export function setSpeedFactor(f) {
    speedFactor = f > 0 ? f : 1
    const base = (SIM_CONFIG.closeHour - SIM_CONFIG.openHour) / SIM_CONFIG.playSeconds
    if (timeController) timeController.setSpeed(base * speedFactor)
    if (timeSpeedBtn) timeSpeedBtn.textContent = speedFactor + 'x'
}

// 拖动时间进度条：定位到 8:00~18:00 内任意时刻，热力/等值线/粒子/在园人数实时联动
export function setSimTime(hour) {
    if (!timeController) return
    timeController.setHour(Math.max(SIM_CONFIG.openHour, Math.min(SIM_CONFIG.closeHour, hour)))
}

export function setQuality(q) {
    CONFIG.quality = q === 'low' ? 'low' : 'high'
    if (ssaaPass) ssaaPass.sampleLevel = CONFIG.quality === 'high' ? 2 : 1
    if (hudButtons) hudButtons.btnQuality.textContent = '画质 ' + (CONFIG.quality === 'high' ? '高' : '低')
}

export function togglePause() {
    if (!timeController) return false
    if (timeController.running) {
        timeController.stop()
        return false
    }
    timeController.start()
    return true
}

export function setParticleCount(n) {
    if (!scene) return
    n = Math.max(200, Math.min(20000, Math.floor(n)))
    CONFIG.particleCount = n
    if (ptsMesh) {
        if (ptsMesh.material.map) ptsMesh.material.map.dispose()
        ptsMesh.geometry.dispose()
        scene.remove(ptsMesh)
        ptsMesh = null
    }
    if (trailMesh) {
        trailMesh.geometry.dispose()
        scene.remove(trailMesh)
        trailMesh = null
    }
    cellHead = null
    cellNext = null
    if (arrowMesh) { arrowMesh.dispose(); scene.remove(arrowMesh); arrowMesh = null }
    initParticles()
    initArrows()
}

export function getSceneStats() {
    return {
        fps: fpsValue,
        drawCalls: renderer ? renderer.info.render.calls : 0,
        particles: CONFIG.particleCount,
        contours: Math.ceil(CONFIG.contourLevels / CONFIG.contourStride),
        columns: columns.length,
        alarms: columns.filter((c) => c.alarm).length,
        deltaKb: deltaSync ? deltaSync.getLoadKB() : 0,
        latencyMs: deltaSync ? deltaSync.getLatencyMs() : 0,
        hour: timeController ? timeController.getCurrentHour() : 8,
        lod: { ...lodCounts },
        exposure,
    }
}
