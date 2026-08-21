<template>
    <div class="search-portal-overlay">
        <div class="portal-search">
            <input
                    type="text"
                    v-model="query"
                    placeholder="搜索目的地 / 景点 / 国家..."
                    @keyup.enter="doSearch"
            />
            <button @click="doSearch">搜索</button>
        </div>
        <div class="portal-cats">
            <div class="portal-cat" @click="$emit('go-spots')">
                <span class="cat-icon">🎫</span>
                <span>景点门票</span>
            </div>
            <div class="portal-cat" @click="$emit('cat', 'hotel')">
                <span class="cat-icon">🏨</span>
                <span>酒店</span>
            </div>
            <div class="portal-cat" @click="$emit('cat', 'flight')">
                <span class="cat-icon">✈️</span>
                <span>机票</span>
            </div>
            <div class="portal-cat" @click="$emit('cat', 'ai')">
                <span class="cat-icon">🤖</span>
                <span>AI 助手</span>
            </div>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'SearchPortal',
        props: {
            value: { type: String, default: '' }
        },
        data() {
            return { query: this.value }
        },
        watch: {
            value(v) { this.query = v }
        },
        methods: {
            doSearch() {
                this.$emit('search', this.query)
            }
        }
    }
</script>

<style scoped>
    .search-portal-overlay {
        position: fixed;
        bottom: 40px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 100;
        width: min(580px, 92vw);
        background: rgba(8, 14, 28, 0.72);
        backdrop-filter: blur(14px);
        -webkit-backdrop-filter: blur(14px);
        border: 1px solid rgba(120, 170, 255, 0.25);
        border-radius: 16px;
        padding: 14px 16px;
        color: #fff;
        box-shadow: 0 8px 30px rgba(0, 0, 0, 0.35);
    }
    .portal-search {
        display: flex;
        gap: 8px;
    }
    .portal-search input {
        flex: 1;
        background: rgba(255, 255, 255, 0.1);
        border: 1px solid rgba(120, 170, 255, 0.3);
        border-radius: 8px;
        padding: 9px 12px;
        color: #fff;
        outline: none;
        font-size: 14px;
    }
    .portal-search input::placeholder {
        color: rgba(255, 255, 255, 0.5);
    }
    .portal-search button {
        background: #3a7bff;
        border: none;
        border-radius: 8px;
        padding: 0 18px;
        color: #fff;
        cursor: pointer;
        font-size: 14px;
    }
    .portal-cats {
        display: flex;
        gap: 8px;
        margin-top: 12px;
    }
    .portal-cat {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        padding: 9px 0;
        border-radius: 10px;
        background: rgba(255, 255, 255, 0.06);
        cursor: pointer;
        transition: background 0.2s;
        font-size: 12px;
    }
    .portal-cat:hover {
        background: rgba(58, 123, 255, 0.28);
    }
    .cat-icon {
        font-size: 20px;
    }
</style>