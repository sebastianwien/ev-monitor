// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../i18n'
import AutoSyncUnsupportedNotice from '../AutoSyncUnsupportedNotice.vue'

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
})

async function mountNotice(carName: string): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AutoSyncUnsupportedNotice, { carName })
    app.use(i18n)
    app.component('router-link', { props: ['to'], template: '<a :href="to"><slot /></a>' })
    app.mount(host)
    await nextTick()
    return host
}

describe('AutoSyncUnsupportedNotice', () => {
    it('nennt das Fahrzeug in der Überschrift', async () => {
        const host = await mountNotice('Zeekr 001')

        expect(host.querySelector('h1')?.textContent).toContain('Zeekr 001')
    })

    it('führt zu Erfassen und Import, nicht zu einem Kauf', async () => {
        const host = await mountNotice('Leapmotor B10')

        const targets = [...host.querySelectorAll('a')].map(a => a.getAttribute('href'))
        expect(targets).toEqual(['/erfassen', '/imports'])
    })
})
