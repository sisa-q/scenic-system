// ============================================================
// dataSimulator.js — 无硬件依赖的客流动态模拟引擎（多日版）
// ============================================================
// 设计要点：
//  1) 建筑客流模型：为每座建筑定义客流峰值时段(peakHours)与基础流量(baseFlow)，
//     结合时间因子函数与高斯衰减模型，生成随时间连续变化的网格密度数据；
//  2) 全天 8:00~18:00 加速为 30 秒循环演示（默认 speed = 10 小时 / 30 秒）；
//  3) 多日剧本 DAY_PLAYBOOK：国庆 10 天（09-28~10-07），每天由 6 个标定参数驱动，
//     10 天 × 30 秒/天 = 300 秒连续播放；虚拟时间 = (dayIndex, hour)；
//  4) 确定性预计算（无随机抖动）：当前在园、今日入园、今日订单、各时段占比与
//     (天, 时刻) 一一对应，拖动进度条可查看任意时刻状态；
//  5) 数据驱动标定：intensityOf / colorTOf / columnHeightOf / speedOf 把"在园人数"
//     映射为密度场强度 / 热力色阶 / 柱体高度 / 粒子速度，保证粒子、等值线、柱体
//     与模拟数据严格对接（同一 (day,hour) 恒有同一视觉量，不同日期间有明确量级差）。
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
// 国庆 10 天剧本（唯一事实来源：前端引擎 / 曲线导出 / 后端落库共用）
// 每天 6 个标定参数：totalEntries(日入园总量) / amQuota,pmQuota(时段配额) /
// amBookedRatio,pmBookedRatio(时段预约率) / refundRate(退款率) / weather(天气)
// ============================================================
export const DAY_PLAYBOOK = [
    { date: '2026-09-28', dayName: '周一', phase: '节前基线', totalEntries: 16000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.40, pmBookedRatio: 0.30, refundRate: 0.02, weather: { text: '晴', temp: 24, rainProb: 0, windLevel: 2 } },
    { date: '2026-09-29', dayName: '周二', phase: '节前爬升', totalEntries: 18000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.50, pmBookedRatio: 0.40, refundRate: 0.02, weather: { text: '多云', temp: 25, rainProb: 0, windLevel: 2 } },
    { date: '2026-09-30', dayName: '周三', phase: '节前预售峰值', totalEntries: 32000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.85, pmBookedRatio: 0.60, refundRate: 0.02, weather: { text: '晴', temp: 26, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-01', dayName: '周四', phase: '假期首日', totalEntries: 52000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 1.00, pmBookedRatio: 0.80, refundRate: 0.02, weather: { text: '多云转晴', temp: 27, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-02', dayName: '周五', phase: '假期高位', totalEntries: 66000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.92, pmBookedRatio: 0.78, refundRate: 0.08, weather: { text: '中雨', temp: 20, rainProb: 75, windLevel: 3 } },
    { date: '2026-10-03', dayName: '周六', phase: '假期峰值', totalEntries: 74000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 1.00, pmBookedRatio: 0.88, refundRate: 0.03, weather: { text: '晴', temp: 34, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-04', dayName: '周日', phase: '高位回落', totalEntries: 60000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.95, pmBookedRatio: 0.60, refundRate: 0.02, weather: { text: '多云', temp: 28, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-05', dayName: '周一', phase: '返程前', totalEntries: 54000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.80, pmBookedRatio: 0.55, refundRate: 0.02, weather: { text: '晴', temp: 26, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-06', dayName: '周二', phase: '返程', totalEntries: 38000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.60, pmBookedRatio: 0.40, refundRate: 0.02, weather: { text: '晴', temp: 24, rainProb: 0, windLevel: 2 } },
    { date: '2026-10-07', dayName: '周三', phase: '假期尾声', totalEntries: 26000, amQuota: 44000, pmQuota: 36000, amBookedRatio: 0.50, pmBookedRatio: 0.30, refundRate: 0.02, weather: { text: '多云转阴', temp: 22, rainProb: 20, windLevel: 4 } },
]

// ============================================================
// 模拟引擎配置（全天 8:00~18:00 → 每 30 秒一天；10 天 = 300 秒）
// ============================================================
export const SIM_CONFIG = {
    openHour: 8,
    closeHour: 18,
    playSeconds: 30,              // 每天加速演示时长（秒）
    capacity: 80000,              // 故宫日承载上限（数据驱动标定基准）
    days: DAY_PLAYBOOK,           // 多日剧本；置空则回退为单日（totalEntries）
    totalEntries: 80000,          // 单日回退：今日入园总量
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
        this.days = (cfg.days && cfg.days.length)
                ? cfg.days
                : [{ totalEntries: cfg.totalEntries, amBookedRatio: 1, pmBookedRatio: 1, refundRate: 0.02 }]
        this.dayCount = this.days.length
        this._build()
    }

    // 预计算每天分钟级曲线（确定性，无随机）
    _build() {
        const { openHour, closeHour } = this.cfg
        const minutes = (closeHour - openHour) * 60
        this.minutes = minutes
        this.dayCurves = this.days.map((day) => this._buildDay(day, minutes))
        // 兼容单日字段（指向第 1 天）
        this.entryRate = this.dayCurves[0].entryRate
        this.cumEntries = this.dayCurves[0].cumEntries
        this.inPark = this.dayCurves[0].inPark
        this.hourlyProportion = this.dayCurves[0].hourlyProportion
        this.totalEntries = this.dayCurves[0].totalEntries
        this.totalOrders = this.dayCurves[0].totalOrders
        this.peakInPark = this.dayCurves[0].peakInPark
    }

    _buildDay(day, minutes) {
        const { openHour } = this.cfg
        const totalEntries = day.totalEntries || 0

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

        // 2) 累计入园（今日入园累计曲线）
        const cum = new Float64Array(minutes)
        let acc = 0
        for (let m = 0; m < minutes; m++) { acc += entryRate[m]; cum[m] = acc }

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
        const surv = new Float64Array(hi + 1)
        let s = 0
        for (let d = hi; d >= 0; d--) { s += pdf[d]; surv[d] = s }
        const inPark = new Float64Array(minutes)
        for (let m = 0; m < minutes; m++) {
            let v = 0
            const dMax = Math.min(hi, m)
            for (let d = 0; d <= dMax; d++) v += entryRate[m - d] * surv[d]
            inPark[m] = v
        }

        // 4) 各时段入园占比（10 个整点时段 8-9 ... 17-18）
        const hourCount = this.cfg.closeHour - this.cfg.openHour
        const hourly = []
        for (let k = 0; k < hourCount; k++) {
            let v = 0
            for (let m = k * 60; m < (k + 1) * 60 && m < minutes; m++) v += entryRate[m]
            hourly.push(v)
        }
        const total = hourly.reduce((a, b) => a + b, 0) || 1
        let peakInPark = 0
        for (let m = 0; m < minutes; m++) if (inPark[m] > peakInPark) peakInPark = inPark[m]

        return {
            day,
            entryRate,
            cumEntries: cum,
            inPark,
            hourlyEntries: hourly,
            hourlyProportion: hourly.map((v) => v / total),
            totalEntries: total,
            totalOrders: Math.round(total * this.cfg.orderRate),
            peakInPark,
        }
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

    // 任意时刻状态：(dayIndex, hour)；省略 dayIndex 时取第 1 天（兼容旧调用）
    getStateAt(dayIndex, hour) {
        if (typeof hour === 'undefined') { hour = dayIndex; dayIndex = 0 }
        const di = Math.max(0, Math.min(this.dayCount - 1, dayIndex | 0))
        const day = this.dayCurves[di]
        const m = this.minuteIndex(hour)
        return {
            dayIndex: di,
            hour,
            minute: m,
            entryRate: day.entryRate[m],
            entriesSoFar: day.cumEntries[m],
            inPark: Math.round(day.inPark[m]),
        }
    }

    // 某天最终数据（与时刻无关的汇总）；省略 dayIndex 时取第 1 天
    getSummary(dayIndex = 0) {
        const di = Math.max(0, Math.min(this.dayCount - 1, dayIndex | 0))
        const day = this.dayCurves[di]
        return {
            totalEntries: Math.round(day.totalEntries),
            totalOrders: day.totalOrders,
            cumulativeVisitors: this.cfg.cumulativeVisitors,
            hourlyProportion: day.hourlyProportion,
            peakInPark: Math.round(day.peakInPark),
            date: day.day && day.day.date,
            dayName: day.day && day.day.dayName,
            phase: day.day && day.day.phase,
            amQuota: day.day && day.day.amQuota,
            pmQuota: day.day && day.day.pmQuota,
            amBookedRatio: day.day && day.day.amBookedRatio,
            pmBookedRatio: day.day && day.day.pmBookedRatio,
            refundRate: day.day && day.day.refundRate,
            weather: day.day && day.day.weather,
        }
    }
}

// ============================================================
// 数据驱动标定（粒子/等值线/柱体/热力 与 模拟数据严格对接）
// 约定：密度场 v = 空间形状(hour) × intensity(inPark)；
//       标定上限 FIELD_CAL_MAX = 满承载峰值场值 × 剧本最大强度（调用方计算）。
// ============================================================

// 在园人数 → 全局强度（0..1，gamma 增强中低承载差异）
export function intensityOf(inPark, capacity = 80000) {
    if (!capacity || capacity <= 0) return 0
    const r = Math.max(0, Math.min(1, inPark / capacity))
    return Math.pow(r, 0.8)
}

// 密度场值 → 热力色阶 t（0..1）
export function colorTOf(v, calMax) {
    if (!calMax || calMax <= 0) return 0
    return Math.max(0, Math.min(1, v / calMax))
}

// 密度场值 → 柱体高度
export function columnHeightOf(v, calMax, base = 0.15, range = 0.8) {
    return base + colorTOf(v, calMax) * range
}

// 密度场值 → 粒子速度（m/s，畅行→停滞）
export function speedOf(v, calMax) {
    const t = colorTOf(v, calMax)
    return Math.max(0.15, 1.4 - t * 1.25)
}

// ============================================================
// 粒子轨迹约束（真实场景：只允许在故宫城墙内活动，从午门进、神武门出）
//  城墙：x ∈ [-5.5, 5.5]，z ∈ [-5, 7.5]（与 createCityWall 一致）
//  南门（午门）门洞 |x|<=1.0 为入口；北门（神武门）门洞 |x|<=1.0 为唯一出口
//  纯函数设计：可在 Node 中直接单测，保证"粒子不越墙、统一进出"
// ============================================================
export const PARTICLE_BOUNDS = {
    xMin: -5.35, xMax: 5.35,     // 城墙内缩 margin，避免贴墙抖动
    zMin: -4.55, zMax: 7.25,
    gateHalf: 1.0,               // 门洞半宽（午门/神武门）
    entranceZ: -4.6,             // 入口（南门内侧出生带）
    exitZ: 7.3,                  // 出口（北门内侧判定线）
}

// 在入口（南门内侧）生成一个粒子：位置落在门洞内、初速向北
export function spawnParticle(rnd = Math.random) {
    const B = PARTICLE_BOUNDS
    return {
        x: (rnd() - 0.5) * 1.8,
        y: 0.15,
        z: B.entranceZ + (rnd() - 0.5) * 0.2,
        vx: (rnd() - 0.5) * 0.04,
        vz: 0.06 + rnd() * 0.06,
        life: 90 + rnd() * 120,
        wander: rnd() < 0.35 ? 1 : 0,   // 35% 为"游荡者"：北流弱、横向扩散大（丰富侧院轨迹）
    }
}

// 单步边界约束：返回 {x,z,vx,vz}；若到达北门出口（门洞内且越过 zMax）返回 {exit:true}
export function stepParticleBound(x, z, vx, vz) {
    const B = PARTICLE_BOUNDS
    // 东西墙
    if (x < B.xMin) { x = B.xMin; if (vx < 0) vx = -vx * 0.5 }
    else if (x > B.xMax) { x = B.xMax; if (vx > 0) vx = -vx * 0.5 }
    // 南墙（午门门洞为入口，不在南侧外出）
    if (z < B.zMin) {
        if (Math.abs(x) <= B.gateHalf) {
            if (z < B.zMin - 0.25) z = B.zMin - 0.25
            if (vz < 0) vz = -vz * 0.5
        } else {
            z = B.zMin
            if (vz < 0) vz = -vz * 0.5
        }
    }
    // 北墙（神武门门洞为唯一出口：越过即离开，调用方负责重生回入口）
    if (z > B.zMax) {
        if (Math.abs(x) <= B.gateHalf) return { exit: true }
        z = B.zMax
        if (vz > 0) vz = -vz * 0.5
    }
    return { x, z, vx, vz }
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
// 时间控制器（dt 驱动，(dayIndex, hour) 多日循环）
//   单日：8:00~18:00 循环；多日：播完 10 小时自动进下一天，10 天共 300 秒
// ============================================================
export class TimeController {
    constructor(startHour = 8, speedHoursPerSec = 1 / 3, dayCount = 1) {
        this.hour = startHour
        this.speed = speedHoursPerSec   // 小时/秒：10 小时 / 30 秒 ≈ 0.3333
        this.dayIndex = 0
        this.dayCount = Math.max(1, dayCount | 0)
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
    // 定位到任意 (天, 时刻) 并同步触发场景更新
    setTime(dayIndex, h) {
        this.dayIndex = Math.max(0, Math.min(this.dayCount - 1, dayIndex | 0))
        this.hour = Math.max(this.openHour, Math.min(this.closeHour, h))
        this.listeners.forEach((fn) => fn(this.dayIndex, this.hour))
    }
    // 兼容：仅改小时（保持当前天）
    setHour(h) { this.setTime(this.dayIndex, h) }
    // 仅改天（保持当前时刻）
    setDay(d) { this.setTime(d | 0, this.hour) }
    setSpeed(s) { this.speed = s }
    getCurrentHour() { return this.hour }
    getCurrentDay() { return this.dayIndex }
    _loop() {
        if (!this.running) return
        const now = performance.now()
        const dt = Math.min(0.1, (now - this._last) / 1000)
        this._last = now
        this.hour += this.speed * dt
        if (this.hour > this.closeHour) {
            // 播完一天自动进下一天；最后一天回到第 1 天（循环演示）
            this.hour = this.openHour
            this.dayIndex = (this.dayIndex + 1) % this.dayCount
        }
        this.listeners.forEach((fn) => fn(this.dayIndex, this.hour))
        this.rafId = requestAnimationFrame(() => this._loop())
    }
}
