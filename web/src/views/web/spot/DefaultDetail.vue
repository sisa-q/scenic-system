<template>
    <div class="default-detail page-container">
        <div class="spot-header">
            <h2>{{ spot.name }}</h2>
            <p class="location">{{ spot.location }}</p>
            <p class="desc">{{ spot.description }}</p>
        </div>

        <div class="closed-banner" v-if="spotClosed">
            ⚠ 该景点暂停开放，暂不可购票
        </div>

        <div v-if="spotClosed">
            <van-empty description="暂不可购票" />
        </div>
        <div v-else-if="groupedSlots && groupedSlots.length">
            <div v-for="(group, index) in groupedSlots" :key="index">
                <div class="policy-title">
                    {{ group.policyName }} - ¥{{ group.price }}
                </div>
                <div class="slot-list">
                    <van-cell
                            v-for="slot in group.slots"
                            :key="slot.id"
                            :title="formatDateTime(slot.startTime) + ' - ' + formatDateTime(slot.endTime)"
                            :label="'余票：' + (slot.quota - slot.booked) + ' 张'"
                            :value="'¥' + group.price"
                            is-link
                            @click="selectSlot(slot, group)"
                    />
                </div>
            </div>
        </div>
        <van-empty v-else description="暂无可用时段" />

        <TabBar />
    </div>
</template>

<script>
    import { getSpotDetail, getSlotsBySpot } from '@/api/ticket'
    import TabBar from '@/components/web/TabBar.vue'
    import { showToast } from 'vant'

    export default {
        name: 'DefaultDetail',
        components: { TabBar },
        data() {
            return {
                spot: {},
                slots: [],
                groupedSlots: []
            }
        },
        computed: {
            // 景点是否停用
            spotClosed() {
                return this.spot && this.spot.status === 0
            }
        },
        async mounted() {
            const id = this.$route.params.id
            if (id) {
                await this.fetchSpot(id)
                await this.fetchSlots(id)
            }
        },
        methods: {
            async fetchSpot(id) {
                try {
                    const res = await getSpotDetail(id)
                    this.spot = (res && res.data) || res || {}
                } catch (e) {
                    console.error('获取景点详情失败:', e)
                    showToast('获取景点信息失败')
                }
            },
            async fetchSlots(id) {
                try {
                    const res = await getSlotsBySpot(id)
                    let slotsData = []
                    if (res && res.data && Array.isArray(res.data)) {
                        slotsData = res.data
                    } else if (res && Array.isArray(res)) {
                        slotsData = res
                    }
                    this.slots = slotsData.filter(slot => slot.status === 1)
                    this.groupSlotsByPolicy()
                } catch (e) {
                    console.error('获取时段失败:', e)
                    this.slots = []
                    this.groupedSlots = []
                }
            },
            groupSlotsByPolicy() {
                const groups = {}
                for (const slot of this.slots) {
                    const key = slot.policyId
                    if (!groups[key]) {
                        groups[key] = {
                            policyId: slot.policyId,
                            policyName: slot.policyName || '未知票种',
                            price: slot.price || 0,
                            slots: []
                        }
                    }
                    groups[key].slots.push(slot)
                }
                this.groupedSlots = Object.values(groups)
            },
            formatDateTime(dateTimeStr) {
                if (!dateTimeStr) return ''
                const normalized = dateTimeStr.replace('T', ' ')
                const parts = normalized.split(' ')
                if (parts.length >= 2) {
                    return parts[0].slice(5) + ' ' + parts[1].slice(0, 5)
                }
                return dateTimeStr
            },
            selectSlot(slot, group) {
                if (this.spotClosed) {
                    showToast('该景点暂停开放')
                    return
                }
                if (slot.status !== 1) {
                    showToast('该时段已关闭')
                    return
                }
                this.$router.push({
                    path: '/order-confirm',
                    query: {
                        slotId: slot.id,
                        spotId: this.spot.id,
                        price: group.price,
                        policyName: group.policyName,
                        spotName: this.spot.name,
                        startTime: slot.startTime,
                        endTime: slot.endTime,
                        quota: slot.quota,
                        booked: slot.booked,
                    }
                })
            }
        }
    }
</script>

<style scoped>
    .default-detail {
        padding-bottom: 60px;
        background: transparent;
        min-height: 100vh;
    }
    .spot-header {
        background: #fff;
        padding: 20px 16px;
        margin-bottom: 12px;
    }
    .spot-header h2 {
        font-size: 20px;
        margin-bottom: 6px;
    }
    .location {
        color: #969799;
        font-size: 14px;
    }
    .desc {
        color: #646566;
        font-size: 14px;
        margin-top: 8px;
    }
    .policy-title {
        font-size: 16px;
        font-weight: 600;
        padding: 16px 16px 8px;
        color: #323233;
    }
    .slot-list {
        padding: 0 16px;
    }

    .closed-banner {
        margin: 0 16px 12px;
        padding: 12px 16px;
        border-radius: 12px;
        background: #fff1f0;
        border: 1px solid #ffa39e;
        color: #cf1322;
        font-size: 15px;
        font-weight: 600;
        text-align: center;
    }
</style>