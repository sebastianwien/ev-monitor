import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
    server: {
        proxy: {
            // Connectors service — must come before /api to take precedence
            '/api/tesla': {
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
        VitePWA({
            registerType: 'autoUpdate',
            includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'masked-icon.svg'],
            workbox: {
                navigateFallbackDenylist: [/^\/sitemap\.xml$/, /^\/robots\.txt$/],
            },
            manifest: {
                name: 'EV Monitor',
                short_name: 'EV Monitor',
                description: 'Track your Electric Vehicle logs',
                theme_color: '#ffffff',
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
                    }
                ]
            }
        })
    ]
})
