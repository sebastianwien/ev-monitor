// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import de from '../../../locales/de.yaml'
import type { PublicModelRatings } from '../../../api/ratingService'
import ModelRatingsBlock from '../ModelRatingsBlock.vue'

/** Was im Product-JSON-LD als Bewertung steht, muss sichtbar sein: Gesamtnote auf Karte, Bericht und Schnitt. */
let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
})

function ratings(over: Partial<PublicModelRatings>): PublicModelRatings {
    return {
        count: 0, rebuy: { yes: 0, brandOtherModel: 0, no: 0 }, rebuyReasons: [], categories: null, totalCost: null,
        strengths: [], weaknesses: [], singles: [], quotes: [], reports: [], aggregate: null, ...over,
    }
}

async function mount(r: PublicModelRatings): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const i18n = createI18n({ legacy: false, locale: 'de', messages: { de }, missingWarn: false, fallbackWarn: false })
    app = createApp({ render: () => h(ModelRatingsBlock, { ratings: r, model: 'Testauto', ctaCarId: null, registerPath: null }) })
    app.use(i18n)
    app.use(createPinia())
    app.mount(host)
    await nextTick()
    return host
}

describe('ModelRatingsBlock Gesamtnote', () => {
    it('zeigt die Gesamtnote auf jeder Einzelkarte und jedem Bericht', async () => {
        const host = await mount(ratings({
            count: 2, rebuy: { yes: 2, brandOtherModel: 0, no: 0 },
            singles: [
                { rebuy: 'YES', dailyFit: 4, longDistance: 5, software: 4, buildQuality: 4, service: null, totalCost: 'EXPECTED', kmRounded: 25_000, businessCar: false, ratingValue: 4.3, withReport: false },
                { rebuy: 'YES', dailyFit: 3, longDistance: 4, software: 4, buildQuality: 4, service: null, totalCost: 'CHEAPER', kmRounded: 10_000, businessCar: false, ratingValue: 3.8, withReport: true },
            ],
            reports: [{ text: 'Bericht', pros: [], cons: [], kmRounded: 10_000, month: '2026-10', logCount: 20, consumptionKwhPer100km: 16.5, ratingValue: 3.8, publishedOn: '2026-10-01' }],
        }))
        const text = host.textContent ?? ''

        expect(text).toContain('4,3 von 5')
        expect((text.match(/3,8 von 5/g) ?? []).length).toBe(2)
        expect(host.querySelectorAll('[role="img"][aria-label^="Gesamtnote"]').length).toBe(3)
    })

    it('zeigt ab 5 Bewertungen den Schnitt mit Anzahl und Erklärung', async () => {
        const host = await mount(ratings({ count: 7, rebuy: { yes: 5, brandOtherModel: 0, no: 2 }, aggregate: { ratingValue: 4.14, ratingCount: 7 } }))

        expect(host.textContent).toContain('Gesamtnote')
        expect(host.textContent).toContain('4,1 von 5')
        expect(host.textContent).toContain('aus 7 Bewertungen')
        expect(host.textContent).toContain('Fahrer mit vielen Kilometern zählen mehr')
    })
})
