const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  devServer: {
    historyApiFallback: true,   // 支持 history 路由直接访问（如 /login）
    port: 8081,                    // 管理端端口
    proxy: {
      '/api': {
        target: 'http://localhost:8083',   // 后端 Spring Boot 端口
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        ws: true
      }
    }
  }
})