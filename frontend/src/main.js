import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'

import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())

// each tab signs in on its own: a new or duplicated tab gets its session before the first page renders
useAuthStore().initTab().finally(() => {
  app.use(router)
  app.mount('#app')
})
