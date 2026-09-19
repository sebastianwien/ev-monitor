import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import path from 'path'

export default defineConfig({
    // Gleicher '@'-Alias wie in vite.config.ts - Stores wie wallbox.ts importieren darueber,
    // ohne den Alias bricht jeder Test, der so einen Store laedt.
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    plugins: [
        // Erlaubt den Import von .vue-Dateien in Tests (Komponenten-DOM-Tests).
        vue(),
        VueI18nPlugin({
            include: [path.resolve(__dirname, './src/locales/**')],
            strictMessage: false,
        }),
    ],
    test: {
        include: ['src/**/*.test.ts'],
        globals: true,
        setupFiles: ['./src/__tests__/setup.ts'],
    },
})
