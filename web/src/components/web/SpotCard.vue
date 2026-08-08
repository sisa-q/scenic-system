<template>
    <div class="spot-card" @click="$emit('click')">
        <div class="spot-image" :style="{ backgroundImage: `url(${image})` }"></div>
        <div class="spot-info">
            <div class="spot-name">{{ name }}</div>
            <div class="spot-location">{{ location }}</div>
            <div class="spot-price">¥{{ price }} 起</div>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'SpotCard',
        props: {
            name: {
                type: String,
                default: ''
            },
            location: {
                type: String,
                default: ''
            },
            price: {
                type: Number,
                default: 0
            },
            image: {
                type: String,
                default: 'https://picsum.photos/200/120?random=1'
            }
        },
        emits: ['click']
    }
</script>

<style scoped>
    .spot-card {
        display: flex;
        background: #fff;
        border-radius: 12px;
        overflow: hidden;
        margin-bottom: 12px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
        cursor: pointer;
    }
    .spot-image {
        width: 120px;
        height: 100px;
        flex-shrink: 0;
        background-size: cover;
        background-position: center;
    }
    .spot-info {
        flex: 1;
        padding: 12px 14px;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
    }
    .spot-name {
        font-size: 16px;
        font-weight: 600;
        color: #323233;
    }
    .spot-location {
        font-size: 13px;
        color: #969799;
        margin-top: 4px;
    }
    .spot-price {
        font-size: 18px;
        font-weight: 700;
        color: #ee0a24;
    }
</style>