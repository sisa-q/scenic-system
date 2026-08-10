const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  devServer: {
    historyApiFallback: true,
    allowedHosts: 'all',
    port: 8080,
    proxy: {
      '/api': { target: 'http://localhost:8083', changeOrigin: true },
      '/ws': { target: 'http://localhost:8083', changeOrigin: true, ws: true }
    }
  },
  pwa: {
    name: '智慧景区全息导览',
    themeColor: '#0a0e27',
    msTileColor: '#0a0e27',
    appleMobileWebAppCapable: 'yes',
    manifestOptions: {
      name: '智慧景区全息导览',
      short_name: '智慧景区',
      start_url: '/',
      display: 'standalone',
      background_color: '#0a0e27',
      description: '数字化文旅景区票务与游客流量监测系统',
      icons: [
        { src: '/img/icons/android-chrome-192x192.png', sizes: '192x192', type: 'image/png' },
        { src: '/img/icons/android-chrome-512x512.png', sizes: '512x512', type: 'image/png' },
        { src: '/img/icons/android-chrome-maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
      ]
    },
    workboxPluginMode: 'GenerateSW',
    workboxOptions: {
      skipWaiting: true,
      clientsClaim: true,
      navigateFallback: '/index.html',
      runtimeCaching: [
        {
          urlPattern: /\.(png|jpe?g|svg|gif|webp|glb|gltf|bin)$/,
          handler: 'CacheFirst',
          options: {
            cacheName: 'static-images',
            expiration: { maxEntries: 60, maxAgeSeconds: 30 * 24 * 3600 },
            cacheableResponse: { statuses: [0, 200] }
          }
        },
        {
          urlPattern: /\/api\/(spot|ticket|notice|weather|flow|evaluation)\//,
          handler: 'NetworkFirst',
          options: {
            cacheName: 'api-cache',
            networkTimeoutSeconds: 5,
            expiration: { maxEntries: 50, maxAgeSeconds: 60 * 60 }
          }
        }
      ]
    }
  }
})