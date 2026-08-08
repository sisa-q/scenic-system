// ============================================================
//  gugongData.js - 故宫布局数据
// ============================================================

// 故宫主要建筑坐标（x: 左右, z: 前后, 中轴线为 x=0, 北为 z+）
export const GUGONG_LAYOUT = {
    // ===== 南端入口 =====
    entrance: { x: 0, z: -4.8, name: '午门（入口）', type: 'gate' },

    // ===== 外朝三大殿 =====
    taihe: { x: 0, z: -1.8, name: '太和殿', type: 'hall', capacity: 2000 },
    zhonghe: { x: 0, z: -0.8, name: '中和殿', type: 'hall', capacity: 500 },
    baohe: { x: 0, z: 0.2, name: '保和殿', type: 'hall', capacity: 800 },

    // ===== 内廷后三宫 =====
    qianqing: { x: 0, z: 1.8, name: '乾清宫', type: 'palace', capacity: 600 },
    jiaotai: { x: 0, z: 2.8, name: '交泰殿', type: 'palace', capacity: 300 },
    kunning: { x: 0, z: 3.8, name: '坤宁宫', type: 'palace', capacity: 400 },

    // ===== 东西六宫（简化） =====
    wenhua: { x: -2.2, z: -1.8, name: '文华殿', type: 'palace', capacity: 300 },
    wuying: { x: 2.2, z: -1.8, name: '武英殿', type: 'palace', capacity: 300 },
    fengxian: { x: -2.2, z: 1.8, name: '奉先殿', type: 'palace', capacity: 200 },
    fengxian_east: { x: 2.2, z: 1.8, name: '奉先殿东', type: 'palace', capacity: 200 },

    // ===== 御花园 =====
    yuhua: { x: 0, z: 5.8, name: '御花园', type: 'garden', capacity: 1000 },

    // ===== 北端出口 =====
    shenwu: { x: 0, z: 7.2, name: '神武门（出口）', type: 'gate' },

    // ===== 其他 =====
    yuhua_pavilion: { x: -1.2, z: 6.0, name: '御景亭', type: 'pavilion' },
    yuhua_pavilion2: { x: 1.2, z: 6.0, name: '千秋亭', type: 'pavilion' },
}

// 定义路径（客流流向）
export const GUGONG_PATHS = [
    { from: 'entrance', to: 'taihe', weight: 80, color: 0x44aaff },
    { from: 'taihe', to: 'zhonghe', weight: 70, color: 0x66ddff },
    { from: 'zhonghe', to: 'baohe', weight: 65, color: 0x88ff88 },
    { from: 'baohe', to: 'qianqing', weight: 55, color: 0x88ff88 },
    { from: 'qianqing', to: 'jiaotai', weight: 50, color: 0xffff44 },
    { from: 'jiaotai', to: 'kunning', weight: 45, color: 0xffff44 },
    { from: 'kunning', to: 'yuhua', weight: 40, color: 0xff8844 },
    { from: 'yuhua', to: 'shenwu', weight: 35, color: 0xff8844 },
    // 旁路
    { from: 'taihe', to: 'wenhua', weight: 25, color: 0x44aaff },
    { from: 'taihe', to: 'wuying', weight: 25, color: 0x44aaff },
    { from: 'qianqing', to: 'fengxian', weight: 20, color: 0x66ddff },
    { from: 'qianqing', to: 'fengxian_east', weight: 20, color: 0x66ddff },
    { from: 'yuhua', to: 'yuhua_pavilion', weight: 15, color: 0xff8844 },
    { from: 'yuhua', to: 'yuhua_pavilion2', weight: 15, color: 0xff8844 },
]

// 峰值时段配置
export const PEAK_HOURS = {
    'entrance': [9, 10, 11],
    'taihe': [10, 11, 14, 15],
    'zhonghe': [10, 11, 14],
    'baohe': [11, 14, 15],
    'qianqing': [11, 12, 15, 16],
    'jiaotai': [12, 15],
    'kunning': [12, 13, 16],
    'yuhua': [13, 14, 16, 17],
    'shenwu': [15, 16, 17, 18],
    'wenhua': [10, 11],
    'wuying': [10, 11],
    'fengxian': [12, 13],
    'fengxian_east': [12, 13],
    'yuhua_pavilion': [14, 15],
    'yuhua_pavilion2': [14, 15],
}

// 基础流量（相对值）
export const BASE_FLOW = {
    'entrance': 80,
    'taihe': 100,
    'zhonghe': 60,
    'baohe': 70,
    'qianqing': 65,
    'jiaotai': 40,
    'kunning': 45,
    'yuhua': 75,
    'shenwu': 50,
    'wenhua': 30,
    'wuying': 30,
    'fengxian': 25,
    'fengxian_east': 25,
    'yuhua_pavilion': 15,
    'yuhua_pavilion2': 15,
}