<template>
    <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
    />
</template>

<script>
    export default {
        name: 'Pagination',
        props: {
            total: {
                type: Number,
                default: 0
            },
            page: {
                type: Number,
                default: 1
            },
            limit: {
                type: Number,
                default: 10
            }
        },
        emits: ['update:page', 'update:limit', 'change'],
        computed: {
            currentPage: {
                get() { return this.page },
                set(val) { this.$emit('update:page', val) }
            },
            pageSize: {
                get() { return this.limit },
                set(val) { this.$emit('update:limit', val) }
            }
        },
        methods: {
            handleSizeChange(val) {
                this.$emit('update:limit', val)
                this.$emit('change')
            },
            handleCurrentChange(val) {
                this.$emit('update:page', val)
                this.$emit('change')
            }
        }
    }
</script>