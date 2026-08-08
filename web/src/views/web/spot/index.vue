<template>
    <div>
        <!-- 顶部红色测试横幅（确认加载器已启动）
        <div
                style="background:red;color:white;padding:8px;text-align:center;font-size:18px;position:fixed;top:0;z-index:9999;width:100%;font-weight:bold;"
        >
            🚀 加载器已启动 | SpotId: {{ spotId }}
        </div>
        -->

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
            <div class="spinner"></div>
            <p>正在加载景点页面...</p>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="error" class="error-state">
            <h3>⚠️ 加载失败</h3>
            <p>{{ error }}</p>
            <button @click="reload">重新加载</button>
        </div>

        <!-- 成功加载组件 -->
        <component :is="currentComponent" v-else-if="currentComponent" />

        <!-- 兜底 -->
        <div v-else class="loading-state">暂无内容</div>
    </div>
</template>

<script>
    export default {
        name: 'SpotDetailLoader',
        data() {
            return {
                currentComponent: null,
                spotId: null,
                loading: true,
                error: null,
            }
        },
        async mounted() {
            this.spotId = parseInt(this.$route.params.id)
            console.log('🔍 [加载器] 景点ID:', this.spotId)

            // 景点 ID → 组件映射（后续可扩展）
            const componentMap = {
                1: () => import('@/views/web/spot/GugongDetail.vue'),
                // 2: () => import('@/views/web/spot/EiffelTowerDetail.vue'),
            }

            try {
                const loader = componentMap[this.spotId]
                let comp = null

                if (loader) {
                    console.log(`✅ [加载器] 加载景点 ${this.spotId} 的专属组件...`)
                    const module = await loader()
                    comp = module.default || module
                    console.log(`✅ [加载器] 专属组件加载成功`, comp)
                } else {
                    console.log(`ℹ️ [加载器] 景点 ${this.spotId} 无专属组件，使用通用模板`)
                    const defaultModule = await import('@/views/web/spot/DefaultDetail.vue')
                    comp = defaultModule.default || defaultModule
                }

                this.currentComponent = comp
                this.loading = false
                console.log('✅ [加载器] 最终渲染组件:', comp.name || '匿名组件')
            } catch (err) {
                console.error('❌ [加载器] 组件加载失败:', err)
                this.error = `组件加载失败：${err.message || '未知错误'}`
                this.loading = false

                // 尝试降级到通用模板
                try {
                    console.log('🔄 [加载器] 尝试降级到通用模板...')
                    const defaultModule = await import('@/views/web/spot/DefaultDetail.vue')
                    this.currentComponent = defaultModule.default || defaultModule
                    this.error = null
                    console.log('✅ [加载器] 降级成功，使用通用模板')
                } catch (fallbackErr) {
                    console.error('❌ [加载器] 降级也失败:', fallbackErr)
                    this.error = '通用模板加载失败，请检查文件是否存在'
                }
            }
        },
        methods: {
            reload() {
                window.location.reload()
            }
        }
    }
</script>

<style scoped>
    .loading-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 100vh;
        font-size: 18px;
        color: #999;
    }
    .error-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 100vh;
        color: #e74c3c;
    }
    .error-state h3 {
        margin-bottom: 12px;
    }
    .error-state button {
        margin-top: 16px;
        padding: 8px 24px;
        background: #3498db;
        color: #fff;
        border: none;
        border-radius: 6px;
        cursor: pointer;
    }
    .error-state button:hover {
        background: #2980b9;
    }
    .spinner {
        width: 40px;
        height: 40px;
        border: 4px solid #f3f3f3;
        border-top: 4px solid #3498db;
        border-radius: 50%;
        animation: spin 1s linear infinite;
        margin-bottom: 16px;
    }
    @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }
</style>