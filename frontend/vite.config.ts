import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import path from 'path'

export default defineConfig(({ mode }) => ({
    define: {
        __INTLIFY_JIT_COMPILATION__: true
    },
    server: {
        proxy: {
            // Connectors service - must come before /api to take precedence
            '/api/tesla': {
                target: 'http://localhost:8081',
                changeOrigin: true
            },
            '/api/smartcar': {
                target: 'http://localhost:8081',
                changeOrigin: true
            },
            '/api/vwgroup': {
                target: 'http://localhost:8081',
                changeOrigin: true
            },
            '/api/goe': {
                target: 'http://localhost:8081',
                changeOrigin: true
            },
            // Wallbox service — must come before /api to take precedence
            '/api/wallbox': {
                target: 'http://localhost:8090',
                changeOrigin: true
            },
            '/ocpp/ws': {
                target: 'ws://localhost:8090',
                ws: true,
                changeOrigin: true
            },
            // Core backend
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            // Swagger UI + OpenAPI spec (springdoc-openapi, not under /api)
            '/swagger-ui': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            '/v3/api-docs': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            // OAuth2 (Spring Security endpoints, not under /api)
            '/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            '/login/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src')
        }
    },
    plugins: [
        vue(),
        VueI18nPlugin({
            include: [path.resolve(__dirname, './src/locales/**')],
            strictMessage: false
        }),
        VitePWA({
            // Nativer Build (Capacitor, `--mode app`): KEIN Service Worker.
            // In der Android-WebView (Origin https://localhost) bricht der Workbox-
            // navigateFallback das Layout (das gesamte <html> kollabiert nach einer
            // SPA-Navigation auf 0x0 -> Black-Screen, persistent ueber Neustarts).
            // In der nativen App ist der SW ohnehin nutzlos: Assets sind gebundelt,
            // Updates liefert Capgo. Im Web-Build bleibt die PWA voll aktiv.
            disable: mode === 'app',
            registerType: 'autoUpdate',
            includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'pwa-192x192.png', 'pwa-512x512.png', 'pwa-maskable-512.png'],
            workbox: {
                navigateFallbackDenylist: [/^\/sitemap\.xml$/, /^\/robots\.txt$/, /^\/api\//, /^\/oauth2\//, /^\/login\/oauth2\//, /^\/swagger-ui/, /^\/v3\/api-docs/],
                maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
            },
            manifest: {
                name: 'EV Monitor',
                short_name: 'EV Monitor',
                description: 'Dein Elektroauto-Ladetagebuch - Verbrauch, Kosten und Reichweite tracken',
                theme_color: '#4f46e5',
                background_color: '#4f46e5',
                display: 'standalone',
                display_override: ['window-controls-overlay', 'standalone'],
                start_url: '/?source=pwa',
                id: '/',
                lang: 'de',
                icons: [
                    {
                        src: 'pwa-192x192.png',
                        sizes: '192x192',
                        type: 'image/png'
                    },
                    {
                        src: 'pwa-512x512.png',
                        sizes: '512x512',
                        type: 'image/png'
                    },
                    {
                        src: 'pwa-maskable-512.png',
                        sizes: '512x512',
                        type: 'image/png',
                        purpose: 'maskable'
                    }
                ],
                shortcuts: [
                    {
                        name: 'Ladevorgang erfassen',
                        short_name: 'Erfassen',
                        url: '/erfassen?source=pwa',
                        icons: [{ src: 'pwa-192x192.png', sizes: '192x192' }]
                    }
                ]
            }
        })
    ]
}))
