<template>
    <div ref="containerRef" class="flow-scene"></div>
</template>

<script>
    import { initScene, destroyScene, resizeScene } from '@/utils/threeScene.js'

    export default {
        name: 'FlowScene',
        mounted() {
            this.$nextTick(() => {
                if (this.$refs.containerRef) {
                    initScene(this.$refs.containerRef)
                }
            })
            // 监听窗口大小变化
            this._resizeHandler = () => resizeScene()
            window.addEventListener('resize', this._resizeHandler)
        },
        beforeUnmount() {
            window.removeEventListener('resize', this._resizeHandler)
            destroyScene()
        }
    }
</script>

<style scoped>
    .flow-scene {
        width: 100%;
        height: 100%;
        min-height: 400px;
        position: relative;
        overflow: hidden;
    }
</style>