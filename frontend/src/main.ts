import { createApp } from 'vue'
import './index.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { createHead } from '@unhead/vue/client'

const app = createApp(App)

// Global v-haptic directive
app.directive('haptic', {
  mounted(el) {
    el.addEventListener('pointerdown', () => {
      if (navigator.vibrate?.(10)) return
      try {
        const ctx = new AudioContext()
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.frequency.value = 440
        gain.gain.value = 0.015
        osc.start()
        osc.stop(ctx.currentTime + 0.012)
        osc.onended = () => ctx.close()
      } catch { /* silent */ }
    })
  }
})

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
        // Ignore service worker registration failures — not app errors
        const stack = event.reason instanceof Error ? (event.reason.stack ?? '') : ''
        if (stack.includes('ServiceWorker') || stack.includes('registerSW')) return
        // Ignore browser extension errors (e.g. chrome.runtime.sendMessage from ad blockers, password managers)
        const message = event.reason instanceof Error ? event.reason.message : String(event.reason)
        if (message.includes('runtime.sendMessage') || message.includes('extension')) return
        // Ignore known WebKit/Safari internal autofill errors — not our code
        if (message.includes('autofillFieldData')) return
        // Ignore known browser extension errors (Zotero, etc.)
        if (message.includes('Zotero') || message.includes('Failed to send message')) return
        reportError(event.reason, 'unhandledrejection')
    })
}

app.mount('#app')
