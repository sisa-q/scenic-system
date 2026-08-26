<template>
    <div class="portal-nav">
        <div class="nav-brand">
            <div class="nav-title">✦ 全息导览系统</div>
            <div class="nav-sub">HOLOGRAPHIC NAVIGATION</div>
        </div>
        <div class="nav-search">
            <input
                    type="text"
                    v-model="query"
                    placeholder="搜索目的地 / 景点 / 国家..."
                    @keyup.enter="doSearch"
            />
            <button @click="doSearch">搜索</button>
        </div>
        <div class="nav-right">
            <div class="nav-stats">
                <span>全球景点</span><b>{{ spotCount }}</b>
                <span class="nav-selected">选中：{{ selected || '无' }}</span>
            </div>
            <div class="nav-cats">
                <span class="nav-cat" @click="$emit('go-spots')">景点门票</span>
            </div>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'SearchPortal',
        props: {
            value: { type: String, default: '' },
            spotCount: { type: Number, default: 0 },
            selected: { type: String, default: '' }
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
    .portal-nav {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        z-index: 200;
        display: flex;
        align-items: center;
        gap: 20px;
        padding: 10px 24px;
        background: rgba(8, 14, 28, 0.78);
        backdrop-filter: blur(14px);
        -webkit-backdrop-filter: blur(14px);
        border-bottom: 1px solid rgba(120, 170, 255, 0.2);
        color: #fff;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    }
    .nav-brand {
        display: flex;
        flex-direction: column;
        white-space: nowrap;
    }
    .nav-title {
        font-size: 17px;
        font-weight: 700;
        letter-spacing: 1px;
    }
    .nav-sub {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.5);
        letter-spacing: 2px;
    }
    .nav-search {
        flex: 1;
        display: flex;
        gap: 8px;
        max-width: 520px;
    }
    .nav-search input {
        flex: 1;
        background: rgba(255, 255, 255, 0.1);
        border: 1px solid rgba(120, 170, 255, 0.3);
        border-radius: 8px;
        padding: 8px 12px;
        color: #fff;
        outline: none;
        font-size: 14px;
    }
    .nav-search input::placeholder {
        color: rgba(255, 255, 255, 0.5);
    }
    .nav-search button {
        background: #3a7bff;
        border: none;
        border-radius: 8px;
        padding: 0 16px;
        color: #fff;
        cursor: pointer;
        font-size: 14px;
    }
    .nav-right {
        display: flex;
        align-items: center;
        gap: 16px;
    }
    .nav-stats {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: rgba(255, 255, 255, 0.7);
        white-space: nowrap;
    }
    .nav-stats b {
        color: #6ea8ff;
        font-size: 14px;
    }
    .nav-selected {
        color: rgba(255, 255, 255, 0.5);
        margin-left: 6px;
    }
    .nav-cats {
        display: flex;
        gap: 4px;
    }
    .nav-cat {
        padding: 6px 12px;
        border-radius: 8px;
        cursor: pointer;
        font-size: 13px;
        transition: background 0.2s, color 0.2s;
        white-space: nowrap;
    }
    .nav-cat:hover {
        background: rgba(58, 123, 255, 0.3);
        color: #fff;
    }
    @media (max-width: 768px) {
        .portal-nav {
            flex-wrap: wrap;
            gap: 8px;
            padding: 8px 12px;
        }
        .nav-sub, .nav-selected {
            display: none;
        }
        .nav-search {
            order: 3;
            max-width: none;
            flex-basis: 100%;
        }
        .nav-cat {
            padding: 4px 8px;
            font-size: 12px;
        }
    }
</style>