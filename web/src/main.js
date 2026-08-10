import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

// ====== Vant（游客端 UI） ======
import Vant from 'vant'
import 'vant/lib/index.css'

// ====== 全局样式 ======
import './assets/styles/global.scss'
import './assets/styles/vant-fix.scss'
import './registerServiceWorker'

const app = createApp(App)
app.use(Vant)
app.use(router)
app.use(createPinia())

app.mount('#app')