// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import GapDetail from '../GapDetail.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

describe('GapDetail', () => {
  it('erklärt die Strecke in Alltagssprache, mit Zeitraum und genauen km', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const gap = { from: new Date(2026, 8, 17, 18, 6).toISOString(), to: new Date(2026, 8, 22, 8, 30).toISOString(), distanceKm: 23.7 }
    app = createApp(GapDetail, { gap })
    app.use(createPinia()).use(i18n)
    app.mount(host)
    await nextTick()
    expect(host.querySelector('[data-testid="gap-text"]')?.textContent).toBe(
      'Zwischen 17.09., 18:06 und 22.09., 08:30 ist dein Kilometerstand um 23,7 km gestiegen. Eine Fahrt dazu ist nicht aufgezeichnet.')
  })
})
