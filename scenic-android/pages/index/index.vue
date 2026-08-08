<template>
  <!-- web-view：整页加载你的游客端网页，自动铺满屏幕 -->
  <web-view :src="homeUrl"></web-view>
</template>

<script>
export default {
  data() {
    return {
      // ============================================
      // ★★★ 修改这里：改成你系统的实际访问地址 ★★★
      // 局域网演示：http://192.168.9.197 （手机需连同一 WiFi）
      // 正式上线：   https://你的域名
      // ============================================
      homeUrl: 'http://192.168.9.197'
    }
  }
}
</script>

<style>
</style>