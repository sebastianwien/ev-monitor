import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import path from 'path'

export default defineConfig({
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
