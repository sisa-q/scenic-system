<template>
    <div class="star-rating" :class="{ disabled }">
        <span
                v-for="i in 5"
                :key="i"
                class="star"
                :class="{ active: i <= currentValue }"
                @click.stop="!disabled && setRating(i)"
                @mouseenter="!disabled && (hoverIndex = i)"
                @mouseleave="!disabled && (hoverIndex = 0)"
        >★</span>
    </div>
</template>

<script>
    export default {
        name: 'StarRating',
        props: {
            // Vue 3 v-model 默认属性
            modelValue: {
                type: Number,
                default: 0
            },
            // 兼容旧写法 v-model:value / :value
            value: {
                type: Number,
                default: 0
            },
            disabled: {
                type: Boolean,
                default: false
            }
        },
        emits: ['update:modelValue', 'update:value'],
        data() {
            return { hoverIndex: 0 }
        },
        computed: {
            currentValue() {
                // 优先使用 modelValue（Vue 3 v-model）；为空时回退到 value
                if (this.modelValue !== undefined && this.modelValue !== null && this.modelValue !== 0) {
                    return this.modelValue
                }
                return this.value || 0
            }
        },
        methods: {
            setRating(val) {
                // 同时发布两种事件，兼容 v-model 与 v-model:value
                this.$emit('update:modelValue', val)
                this.$emit('update:value', val)
                // 点击后立即清除悬停状态，使星星固定展示当前评分
                this.hoverIndex = 0
            }
        }
    }
</script>

<style scoped>
    .star-rating {
        display: inline-flex;
        gap: 4px;
        font-size: 28px;
        cursor: pointer;
        line-height: 1;
    }
    .star-rating.disabled {
        cursor: default;
    }
    .star {
        color: #ddd;
        transition: color 0.2s;
        user-select: none;
    }
    .star.active {
        color: #f5a623;
    }
</style>
