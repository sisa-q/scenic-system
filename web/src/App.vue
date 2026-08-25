<template>
  <router-view />
  <AgentFloating />
</template>

<script>
  import AgentFloating from '@/components/web/AgentFloating.vue'

  export default {
    name: 'App',
    components: { AgentFloating }
  }
</script>

<style>
  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
  }
  html,
  body {
    height: 100%;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    font-size: 14px;
    color: #333;
    background: #f7f8fa;
  }
  a {
    color: #409eff;
    text-decoration: none;
  }
</style>