import { createApp } from 'vue'
import './index.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { createHead } from '@unhead/vue/client'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(createHead())

// Global error reporting (production only)
if (import.meta.env.PROD) {
    const reportError = (err: unknown, info: string) => {
        const message = err instanceof Error ? err.message : String(err)
        const stack = err instanceof Error ? err.stack?.substring(0, 2000) : undefined
        fetch(`${import.meta.env.VITE_API_BASE_URL}/errors/frontend`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, stack, url: window.location.href, info }),
        }).catch(() => {})
    }

    app.config.errorHandler = (err, _instance, info) => {
        console.error('Vue Error:', err)
        reportError(err, info)
    }

    window.addEventListener('unhandledrejection', (event) => {
        reportError(event.reason, 'unhandledrejection')
    })
}

app.mount('#app')
