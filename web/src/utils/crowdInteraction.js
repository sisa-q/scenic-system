// ============================================================
// crowdInteraction.js —— 故宫客流密集交互（阶段1 + 阶段2）
// 功能：
//   1) 每栋建筑实时拥挤度分级（绿/黄/橙/红），基地色环热力显示
//   2) 点击建筑 -> 信息卡（名称/人数/容量/拥挤度/等级）
//   3) 疏散预案：点击「疏散」-> 建筑红色闪烁 + 疏散路径动画 + 广播倒计时
// 设计：完全新增模块，不修改原有建筑/LOD/粒子渲染；命中用透明球，安全可回退
// ============================================================
import * as THREE from 'three'
import { GUGONG_LAYOUT, HOTSPOTS } from './dataSimulator.js'

// 注入全局样式（动态元素，scoped 样式不生效；只注入一次）
;(function injectStyle() {
    if (document.getElementById('crowd-style')) return
    const st = document.createElement('style')
    st.id = 'crowd-style'
    st.textContent = `
.crowd-card{position:absolute;right:14px;bottom:120px;z-index:30;width:220px;background:rgba(8,18,38,.92);border:1px solid rgba(102,204,255,.4);border-radius:14px;padding:14px 16px;color:#e8eef8;font-size:13px;backdrop-filter:blur(8px);pointer-events:auto;box-shadow:0 10px 36px rgba(0,0,0,.5)}
.crowd-card-title{font-size:15px;font-weight:700;color:#fff;margin-bottom:8px;letter-spacing:1px}
.crowd-card-row{padding:3px 0;color:rgba(232,238,248,.85)}
.crowd-btn{margin-top:10px;width:100%;padding:8px;border:none;border-radius:10px;background:linear-gradient(135deg,#ff5f3d,#ff3333);color:#fff;font-weight:700;cursor:pointer;font-size:14px}
.crowd-board{position:absolute;top:12px;left:50%;transform:translateX(-50%);z-index:31;display:flex;gap:10px;align-items:center;background:rgba(60,10,10,.78);border:1px solid rgba(255,90,60,.6);color:#ffd9c9;padding:8px 18px;border-radius:24px;font-size:13px;backdrop-filter:blur(8px);pointer-events:none;white-space:nowrap;box-shadow:0 6px 24px rgba(0,0,0,.4)}
.crowd-board-cd{color:#ffcc66;font-weight:700}
`
    document.head.appendChild(st)
})()

const LEVELS = [
    { key: 'green', label: '正常', color: 0x22cc66, ratio: 0.6 },
    { key: 'yellow', label: '偏挤', color: 0xffcc33, ratio: 0.8 },
    { key: 'orange', label: '拥挤', color: 0xff8833, ratio: 0.95 },
    { key: 'red', label: '超限', color: 0xff3333, ratio: Infinity },
]

const HOTSPOT_FLOW = {}
HOTSPOTS.forEach((h) => { HOTSPOT_FLOW[h.id] = h.baseFlow })

// 全天 8~18 时间因子：0.55 ~ 1.0，中午峰值
function flowFactor(hour) {
    const t = (hour - 8) / 10
    return 0.55 + 0.45 * Math.sin(Math.PI * Math.min(1, Math.max(0, t)))
}

function levelOf(density) {
    for (const L of LEVELS) {
        if (density < L.ratio) return L
    }
    return LEVELS[LEVELS.length - 1]
}

export function initCrowdInteraction({ container, renderer, camera, scene, getBuildings, getHour }) {
    const hits = []
    const byId = {}
    const exits = [
        { id: GUGONG_LAYOUT.entrance.id, name: GUGONG_LAYOUT.entrance.name, x: GUGONG_LAYOUT.entrance.x, z: GUGONG_LAYOUT.entrance.z },
        { id: GUGONG_LAYOUT.exit.id, name: GUGONG_LAYOUT.exit.name, x: GUGONG_LAYOUT.exit.x, z: GUGONG_LAYOUT.exit.z },
    ]
    let evac = null
    let hoverId = null
    let rafTick = 0

    ;(getBuildings() || []).forEach((b) => {
        // 透明命中球（可拾取，不可见）
        const hit = new THREE.Mesh(
            new THREE.SphereGeometry(0.34, 10, 10),
            new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, depthWrite: false })
        )
        hit.position.set(b.x, 0.5, b.z)
        hit.userData.buildingId = b.id
        scene.add(hit)

        // 基地色环（分级热力）
        const ring = new THREE.Mesh(
            new THREE.CircleGeometry(0.34, 28),
            new THREE.MeshBasicMaterial({ color: LEVELS[0].color, transparent: true, opacity: 0.55, depthWrite: false, side: THREE.DoubleSide })
        )
        ring.rotation.x = -Math.PI / 2
        ring.position.set(b.x, 0.02, b.z)
        ring.renderOrder = 2
        scene.add(ring)

        const capacity = Math.max(30, Math.round((b.dim ? b.dim.w * b.dim.d : 0.6) * 80))
        const baseFlow = HOTSPOT_FLOW[b.id] || 30
        const info = {
            id: b.id,
            name: GUGONG_LAYOUT[b.id] ? GUGONG_LAYOUT[b.id].name : b.id,
            x: b.x, z: b.z, capacity, baseFlow,
            visitors: 0, density: 0, level: LEVELS[0],
        }
        byId[b.id] = info
        hits.push({ id: b.id, mesh: hit, ring, info })
    })

    // 信息卡
    const card = document.createElement('div')
    card.className = 'crowd-card'
    card.style.display = 'none'
    container.appendChild(card)

    // 广播条
    const board = document.createElement('div')
    board.className = 'crowd-board'
    board.style.display = 'none'
    container.appendChild(board)

    function openCard(info) {
        card.innerHTML = ''
        const title = document.createElement('div')
        title.className = 'crowd-card-title'
        title.textContent = info.name
        card.appendChild(title)
        ;[
            '当前人数：' + Math.round(info.visitors),
            '区域容量：' + info.capacity,
            '拥挤度：' + (info.density * 100).toFixed(0) + '%',
            '等级：' + info.level.label,
        ].forEach((r) => {
            const d = document.createElement('div')
            d.className = 'crowd-card-row'
            d.textContent = r
            card.appendChild(d)
        })
        const btn = document.createElement('button')
        btn.className = 'crowd-btn'
        btn.textContent = evac && evac.id === info.id ? '取消疏散' : '🚨 疏散预案'
        btn.onclick = () => {
            if (evac && evac.id === info.id) {
                cleanupEvac()
            } else {
                startEvac(info)
            }
            openCard(info)
        }
        card.appendChild(btn)
        card.style.display = 'block'
    }

    function nearestExit(info) {
        let best = exits[0], bd = Infinity
        exits.forEach((e) => {
            const d = Math.hypot(e.x - info.x, e.z - info.z)
            if (d < bd) { bd = d; best = e }
        })
        return best
    }

    function startEvac(info) {
        const ex = nearestExit(info)
        const from = new THREE.Vector3(info.x, 0.1, info.z)
        const to = new THREE.Vector3(ex.x, 0.1, ex.z)
        const geo = new THREE.BufferGeometry().setFromPoints([from, to])
        const line = new THREE.Line(geo, new THREE.LineBasicMaterial({ color: 0xffcc33, transparent: true, opacity: 0.9 }))
        line.renderOrder = 3
        scene.add(line)
        const dot = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffe066 }))
        scene.add(dot)
        evac = { id: info.id, info, line, dot, from, to, start: performance.now(), duration: 20 }
        board.innerHTML = ''
        const tag = document.createElement('span')
        tag.textContent = '📢 疏散中：' + info.name + ' → ' + ex.name + '（北出口）'
        board.appendChild(tag)
        const cd = document.createElement('span')
        cd.className = 'crowd-board-cd'
        board.appendChild(cd)
        board.style.display = 'flex'
    }

    function cleanupEvac() {
        if (!evac) return
        scene.remove(evac.line)
        evac.line.geometry.dispose()
        scene.remove(evac.dot)
        evac.dot.geometry.dispose()
        const h = hits.find((x) => x.id === evac.id)
        if (h) h.ring.material.opacity = 0.55
        evac = null
        board.style.display = 'none'
    }

    // 交互
    const raycaster = new THREE.Raycaster()
    const mouse = new THREE.Vector2()
    function toNDC(e) {
        const rect = renderer.domElement.getBoundingClientRect()
        mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
        mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
    }
    function pick(e) {
        toNDC(e)
        raycaster.setFromCamera(mouse, camera)
        const inter = raycaster.intersectObjects(hits.map((h) => h.mesh), false)
        return inter.length ? inter[0].object.userData.buildingId : null
    }
    function onMove(e) {
        const id = pick(e)
        if (id !== hoverId) {
            if (hoverId) {
                const h = hits.find((x) => x.id === hoverId)
                if (h && !(evac && evac.id === hoverId)) h.ring.material.color.setHex(h.info.level.color)
            }
            hoverId = id
            renderer.domElement.style.cursor = id ? 'pointer' : 'default'
        }
    }
    function onClick(e) {
        const id = pick(e)
        if (id) {
            const h = hits.find((x) => x.id === id)
            if (h) openCard(h.info)
        }
    }
    renderer.domElement.addEventListener('pointermove', onMove)
    renderer.domElement.addEventListener('click', onClick)

    // 每帧更新
    function update() {
        rafTick++
        const hour = getHour()
        const factor = flowFactor(hour)
        hits.forEach((h) => {
            const info = h.info
            info.visitors = info.baseFlow * factor * (1 + Math.sin(rafTick * 0.02 + h.id.length) * 0.08)
            info.density = Math.min(1.2, info.visitors / info.capacity)
            info.level = levelOf(info.density)
            if (evac && evac.id === h.id) {
                h.ring.material.color.setHex(0xff3333)
                h.ring.material.opacity = 0.4 + 0.5 * Math.abs(Math.sin(rafTick * 0.15))
            } else if (hoverId !== h.id) {
                h.ring.material.color.setHex(info.level.color)
                h.ring.material.opacity = 0.55
            }
        })
        if (evac) {
            const el = (performance.now() - evac.start) / 1000
            const remain = Math.max(0, evac.duration - el)
            if (remain <= 0) {
                cleanupEvac()
            } else {
                const t = (el % 2) / 2
                evac.dot.position.lerpVectors(evac.from, evac.to, t)
                const cd = board.querySelector('.crowd-board-cd')
                if (cd) cd.textContent = ' · 剩余 ' + Math.ceil(remain) + 's'
            }
        }
    }

    function dispose() {
        renderer.domElement.removeEventListener('pointermove', onMove)
        renderer.domElement.removeEventListener('click', onClick)
        hits.forEach((h) => {
            scene.remove(h.mesh)
            scene.remove(h.ring)
            h.mesh.geometry.dispose()
            h.ring.geometry.dispose()
        })
        cleanupEvac()
        if (card.parentNode) card.parentNode.removeChild(card)
        if (board.parentNode) board.parentNode.removeChild(board)
    }

    return { update, dispose }
}