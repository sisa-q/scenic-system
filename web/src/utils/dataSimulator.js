// ============================================================
// dataSimulator.js — 无硬件依赖的客流动态模拟引擎
// ============================================================
// 设计要点：
//  1) 建筑客流模型：为每座建筑定义客流峰值时段(peakHours)与基础流量(baseFlow)，
//     结合时间因子函数与高斯衰减模型，生成随时间连续变化的网格密度数据；
//  2) 全天 8:00~18:00 加速为 30 秒循环演示（默认 speed = 10 小时 / 30 秒）；
//  3) 确定性预计算（无随机抖动）：当前在园人数、今日入园、今日订单、累计游客、
//     各时段入园占比与时刻一一对应，拖动时间进度条可查看任意时刻的状态。
// ============================================================

export const GUGONG_LAYOUT = {
    wumen: { id: 'wumen', name: '午门', x: 0, z: -3.5 },
    taihe: { id: 'taihe', name: '太和殿', x: 0, z: -1.5 },
    zhonghe: { id: 'zhonghe', name: '中和殿', x: 0, z: -0.5 },
    baohe: { id: 'baohe', name: '保和殿', x: 0, z: 0.5 },
    qianqing: { id: 'qianqing', name: '乾清宫', x: 0, z: 2.0 },
    jiaotai: { id: 'jiaotai', name: '交泰殿', x: 0, z: 3.0 },
    kunning: { id: 'kunning', name: '坤宁宫', x: 0, z: 4.0 },
    shenwu: { id: 'shenwu', name: '神武门', x: 0, z: 5.5 },
    wenhua: { id: 'wenhua', name: '文华殿', x: -2.5, z: -1.5 },
    wuying: { id: 'wuying', name: '武英殿', x: 2.5, z: -1.5 },
    fengxian: { id: 'fengxian', name: '奉先殿', x: -2.5, z: 1.5 },
    fengxian2: { id: 'fengxian2', name: '奉先殿东', x: 2.5, z: 1.5 },
    yuhua: { id: 'yuhua', name: '御花园', x: 0, z: 6.5 },
    entrance: { id: 'entrance', name: '入口', x: 0, z: -4.5 },
    exit: { id: 'exit', name: '出口', x: 0, z: 7.5 },
}

export const HOTSPOTS = [
    { id: 'taihe', name: '太和殿', x: 0, z: -1.5, peakHours: [10, 11, 14, 15], baseFlow: 100 },
    { id: 'qianqing', name: '乾清宫', x: 0, z: 2.0, peakHours: [11, 12, 15, 16], baseFlow: 70 },
    { id: 'yuhua', name: '御花园', x: 0, z: 6.5, peakHours: [13, 14, 16, 17], baseFlow: 60 },
    { id: 'wumen', name: '午门', x: 0, z: -3.5, peakHours: [9, 10, 11], baseFlow: 80 },
    { id: 'wenhua', name: '文华殿', x: -2.5, z: -1.5, peakHours: [10, 11], baseFlow: 40 },
    { id: 'wuying', name: '武英殿', x: 2.5, z: -1.5, peakHours: [10, 11], baseFlow: 40 },
]

export const GRID_CONFIG = { size: 20, width: 10, height: 10 }

// ============================================================
// 模拟引擎配置（全天 8:00~18:00 → 30 秒）
// ============================================================
export const SIM_CONFIG = {
    openHour: 8,
    closeHour: 18,
    playSeconds: 30,              // 全天加速演示时长（秒）
    totalEntries: 80000,          // 今日入园总量（故宫日承载上限量级）
    orderRate: 0.975,             // 订票率 → 今日订单
    cumulativeVisitors: 28600000, // 累计游客（历史总量）
    avgStayMin: 180,              // 平均停留时长 3 小时
    staySpreadMin: 120,           // 停留时长半展 2 小时（约 2.5h ~ 5h）
}

// ============================================================
// 客流动态模拟引擎
// ============================================================
export class SimulationEngine {
    constructor(cfg = SIM_CONFIG) {
        this.cfg = cfg
        this._build()
    }

    // 预计算全天分钟级曲线（确定性，无随机）
    _build() {
        const { openHour, closeHour, totalEntries } = this.cfg
        const minutes = (closeHour - openHour) * 60
        this.minutes = minutes

        // 1) 分钟级入园速率（人/分钟），由时间因子函数加权
        const entryRate = new Float64Array(minutes)
        let weightSum = 0
        for (let m = 0; m < minutes; m++) {
            const hour = openHour + m / 60
            const w = this._weight(hour)
            entryRate[m] = w
            weightSum += w
        }
        for (let m = 0; m < minutes; m++) entryRate[m] = totalEntries * entryRate[m] / weightSum
        this.entryRate = entryRate

        // 2) 累计入园（今日入园累计曲线）
        const cum = new Float64Array(minutes)
        let acc = 0
        for (let m = 0; m < minutes; m++) { acc += entryRate[m]; cum[m] = acc }
        this.cumEntries = cum

        // 3) 当前在园人数 = 入园速率与停留时长生存函数的卷积
        //    停留时长服从三角分布（均值 3 小时，区间约 [2.5h, 5h]）
        const stayMin = this.cfg.avgStayMin
        const spread = this.cfg.staySpreadMin
        const lo = Math.max(0, stayMin - spread)
        const hi = stayMin + spread
        const pdf = new Float64Array(hi + 1)
        for (let d = lo; d <= hi; d++) pdf[d] = 1 - Math.abs(d - stayMin) / spread
        let pSum = 0
        for (let d = lo; d <= hi; d++) pSum += pdf[d]
        for (let d = lo; d <= hi; d++) pdf[d] /= pSum
        // 生存函数 S(d) = P(停留时长 > d)
        const surv = new Float64Array(hi + 1)
        let tail = 0
        for (let d = hi; d >= 0; d--) {
            surv[d] = tail
            tail += pdf[d]
        }
        const inPark = new Float64Array(minutes)
        for (let m = 0; m < minutes; m++) {
            let v = 0
            const dMax = Math.min(hi, m)
            for (let d = 0; d <= dMax; d++) v += entryRate[m - d] * surv[d]
            inPark[m] = v
        }
        this.inPark = inPark

        // 4) 各时段入园占比（10 个整点时段 8-9 ... 17-18）
        const hourly = []
        const hourCount = closeHour - openHour
        for (let k = 0; k < hourCount; k++) {
            let v = 0
            const s = k * 60
            const e = s + 60
            for (let m = s; m < e && m < minutes; m++) v += entryRate[m]
            hourly.push(v)
        }
        this.hourlyEntries = hourly
        const total = hourly.reduce((a, b) => a + b, 0) || 1
        this.hourlyProportion = hourly.map((v) => v / total)
        this.totalEntries = total
        this.totalOrders = Math.round(total * this.cfg.orderRate)
        this.peakInPark = 0
        for (let m = 0; m < minutes; m++) if (inPark[m] > this.peakInPark) this.peakInPark = inPark[m]
    }

    // 时间因子（与热力场共用同一客流规律：9-11 与 14-16 双峰）
    _weight(hour) {
        const w = getTimeFactor(hour)
        // 分钟级平滑扰动（确定性，与时刻一一对应）
        return w * (0.85 + 0.15 * Math.sin(hour * 1.3 + 0.4))
    }

    minuteIndex(hour) {
        const m = Math.round((hour - this.cfg.openHour) * 60)
        return Math.max(0, Math.min(this.minutes - 1, m))
    }

    // 任意时刻状态（拖动进度条即调用此方法）
    getStateAt(hour) {
        const m = this.minuteIndex(hour)
        return {
            hour,
            minute: m,
            entryRate: this.entryRate[m],
            entriesSoFar: this.cumEntries[m],
            inPark: Math.round(this.inPark[m]),
        }
    }

    // 当天最终数据（与时刻无关的汇总）
    getSummary() {
        return {
            totalEntries: Math.round(this.totalEntries),
            totalOrders: this.totalOrders,
            cumulativeVisitors: this.cfg.cumulativeVisitors,
            hourlyProportion: this.hourlyProportion,
            peakInPark: Math.round(this.peakInPark),
        }
    }
}

// ============================================================
// 时间因子（保持原接口，供热力场等使用）
// ============================================================
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

function getHotspotFlow(hotspot, hour) {
    const timeFactor = getTimeFactor(hour)
    let peakBonus = 0
    if (hotspot.peakHours.includes(Math.floor(hour))) peakBonus = 0.5
    const base = hotspot.baseFlow * (timeFactor + peakBonus * 0.3)
    const noise = (Math.random() - 0.5) * 15
    return Math.max(0, base + noise)
}

export function generateGridData(hour, gridSize = GRID_CONFIG.size) {
    const data = []
    const hotspotFlows = {}
    HOTSPOTS.forEach((h) => { hotspotFlows[h.id] = getHotspotFlow(h, hour) })
    for (let i = 0; i < gridSize; i++) {
        data[i] = []
        for (let j = 0; j < gridSize; j++) {
            const wx = (i / gridSize) * GRID_CONFIG.width - GRID_CONFIG.width / 2 + GRID_CONFIG.width / gridSize / 2
            const wz = (j / gridSize) * GRID_CONFIG.height - GRID_CONFIG.height / 2 + GRID_CONFIG.height / gridSize / 2
            let total = 0
            HOTSPOTS.forEach((h) => {
                const dx = wx - h.x, dz = wz - h.z
                const dist2 = dx * dx + dz * dz
                total += hotspotFlows[h.id] * Math.exp(-dist2 / 2)
            })
            total += 2 * (Math.random() - 0.5)
            data[i][j] = Math.max(0, total)
        }
    }
    return data
}

// ============================================================
// 时间控制器（dt 驱动，8:00~18:00 全天循环）
// ============================================================
export class TimeController {
    constructor(startHour = 8, speedHoursPerSec = 1 / 3) {
        this.hour = startHour
        this.speed = speedHoursPerSec   // 小时/秒：10 小时 / 30 秒 ≈ 0.3333
        this.openHour = 8
        this.closeHour = 18
        this.listeners = []
        this.running = false
        this.rafId = null
        this._last = 0
    }
    start() {
        if (this.running) return
        this.running = true
        this._last = performance.now()
        this._loop()
    }
    stop() {
        this.running = false
        if (this.rafId) cancelAnimationFrame(this.rafId)
        this.rafId = null
    }
    onTick(fn) { this.listeners.push(fn) }
    // 拖动进度条：直接定位到任意时刻并同步触发场景更新
    setHour(h) {
        this.hour = Math.max(this.openHour, Math.min(this.closeHour, h))
        this.listeners.forEach((fn) => fn(this.hour))
    }
    setSpeed(s) { this.speed = s }
    getCurrentHour() { return this.hour }
    _loop() {
        if (!this.running) return
        const now = performance.now()
        const dt = Math.min(0.1, (now - this._last) / 1000)
        this._last = now
        this.hour += this.speed * dt
        if (this.hour > this.closeHour) this.hour = this.openHour  // 全天循环演示
        this.listeners.forEach((fn) => fn(this.hour))
        this.rafId = requestAnimationFrame(() => this._loop())
    }
}