// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { i18n } from '../../i18n'
import XpengUpgradeNotice from '../XpengUpgradeNotice.vue'

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
})

function mountNotice(): HTMLElement {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(XpengUpgradeNotice)
    app.use(i18n)
    app.mount(host)
    return host
}

describe('XpengUpgradeNotice', () => {
    it('bietet den XPeng-Weg: Portal-Link, kostenlosen Import und Supporter - keinen AutoSync-Kauf', () => {
        const host = mountNotice()

        // Link zum XPeng Data-Act-Portal (damit der User den Export ueberhaupt anfordern kann).
        expect(host.querySelector('a[href="https://www.xpeng.com/data-act"]')).not.toBeNull()
        // Primaerer Weg: kostenloser ZIP-Import.
        expect(host.querySelector('[to="/imports"]')).not.toBeNull()
        // Sekundaer: Auswertungen via Supporter.
        expect(host.querySelector('[to="/supporter"]')).not.toBeNull()
    })
})
