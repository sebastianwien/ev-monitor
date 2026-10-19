// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SeoOverview, SeoPageRow, SeoQueryRow, SeoStatus } from '../../../api/seoService'

const getSeoStatus = vi.fn()
const getSeoOverview = vi.fn()
const getSeoPages = vi.fn()
const getSeoQueries = vi.fn()
const triggerSeoSync = vi.fn()
vi.mock('../../../api/seoService', () => ({
    getSeoStatus: () => getSeoStatus(),
    getSeoOverview: (d: number) => getSeoOverview(d),
    getSeoPages: (d: number, f: string) => getSeoPages(d, f),
    getSeoQueries: (d: number, b: string, f: string) => getSeoQueries(d, b, f),
    triggerSeoSync: (d: number) => triggerSeoSync(d),
}))
// Chart.js braucht Canvas; in jsdom reicht ein Platzhalter
vi.mock('vue-chartjs', () => ({ Line: { template: '<div class="chart" />' } }))

import AdminSeoTab from '../AdminSeoTab.vue'

const status: SeoStatus = {
    enabled: true,
    dataUntil: '2026-10-16',
    runs: [{ id: 'r1', source: 'GSC', startedAt: '2026-10-19T04:30:00', finishedAt: '2026-10-19T04:31:00', status: 'SUCCESS',
        rowCount: 1234, fromDay: '2026-10-10', toDay: '2026-10-16', error: null }],
}
const overview: SeoOverview = {
    periodDays: 28, from: '2026-09-19', to: '2026-10-16',
    current: { impressions: 184200, clicks: 6410, ctr: 0.0348, avgPosition: 14.2 },
    previous: { impressions: 164500, clicks: 5930, ctr: 0.036, avgPosition: 15.3 },
    days: [{ day: '2026-10-15', impressions: 100, clicks: 3 }, { day: '2026-10-16', impressions: 120, clicks: 4 }],
}
const pages: SeoPageRow[] = [
    { path: '/modelle/Alpha/One', impressions: 41300, clicks: 1820, ctr: 0.044, avgPosition: 6.1, finding: null },
    { path: '/modelle/Beta/Two', impressions: 28900, clicks: 610, ctr: 0.021, avgPosition: 9.8, finding: 'UNDERRATED' },
]
const queries: SeoQueryRow[] = [
    { query: 'alpha one verbrauch <b>x</b>', brand: false, impressions: 12400, clicks: 980, ctr: 0.079, avgPosition: 3.2,
        topPath: '/modelle/Alpha/One', pageCount: 2, finding: 'CANNIBALIZED' },
]

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
    vi.resetAllMocks()
})

async function mount(): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AdminSeoTab)
    app.mount(host)
    for (let i = 0; i < 4; i++) await nextTick()
    return host
}

function click(host: HTMLElement, label: string) {
    const btn = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === label)
    if (!btn) throw new Error(`Button ${label} fehlt`)
    btn.click()
}

describe('AdminSeoTab', () => {
    it('lädt Status und Überblick für 28 Tage, zeigt Kacheln mit Vorzeitraum und Sync-Kopf', async () => {
        getSeoStatus.mockResolvedValue(status)
        getSeoOverview.mockResolvedValue(overview)
        const host = await mount()
        const text = host.textContent ?? ''

        expect(getSeoOverview).toHaveBeenCalledWith(28)
        expect(text).toContain('184.200')
        expect(text).toContain('6.410')
        expect(text).toContain('3,5 %')
        expect(text).toContain('14,2')
        expect(text).toContain('+12 % zum Vorzeitraum')
        expect(text).toContain('besser um 1,1')
        expect(text).toContain('1.234 Zeilen')
        expect(host.querySelectorAll('.chart').length).toBe(1)
    })

    it('wechselt Zeitraum und Unter-Tabs und lädt Seiten und Suchbegriffe mit Filtern', async () => {
        getSeoStatus.mockResolvedValue(status)
        getSeoOverview.mockResolvedValue(overview)
        getSeoPages.mockResolvedValue(pages)
        getSeoQueries.mockResolvedValue(queries)
        const host = await mount()

        click(host, '90 Tage')
        await nextTick()
        expect(getSeoOverview).toHaveBeenLastCalledWith(90)

        click(host, 'Seiten')
        await nextTick()
        await nextTick()
        expect(getSeoPages).toHaveBeenLastCalledWith(90, 'ALL')
        expect(host.textContent).toContain('/modelle/Beta/Two')
        expect(host.textContent).toContain('Unterschätzt')

        click(host, 'Tot')
        await nextTick()
        expect(getSeoPages).toHaveBeenLastCalledWith(90, 'DEAD')

        click(host, 'Suchbegriffe')
        await nextTick()
        await nextTick()
        expect(getSeoQueries).toHaveBeenLastCalledWith(90, 'ALL', 'ALL')
        // fremder Suchbegriff wird als Text gerendert, nie als HTML
        expect(host.querySelector('td b')).toBeNull()
        expect(host.textContent).toContain('alpha one verbrauch <b>x</b>')
        expect(host.textContent).toContain('Kannibalisierung')

        click(host, 'Non-Brand')
        await nextTick()
        expect(getSeoQueries).toHaveBeenLastCalledWith(90, 'NON_BRAND', 'ALL')
    })

    it('zeigt den Leerzustand ohne Zugang und meldet einen abgelehnten Sync', async () => {
        getSeoStatus.mockResolvedValue({ enabled: false, dataUntil: '2026-10-17', runs: [] })
        getSeoOverview.mockResolvedValue({ ...overview, current: { impressions: 0, clicks: 0, ctr: 0, avgPosition: 0 } })
        triggerSeoSync.mockResolvedValue(false)
        const host = await mount()

        expect(host.textContent).toContain('GSC_SERVICE_ACCOUNT_JSON')
        const sync = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Jetzt synchronisieren')!
        expect(sync.disabled).toBe(true)
    })

    it('startet einen Sync und zeigt die Rückmeldung', async () => {
        getSeoStatus.mockResolvedValue(status)
        getSeoOverview.mockResolvedValue(overview)
        triggerSeoSync.mockResolvedValue(true)
        const host = await mount()

        click(host, 'Jetzt synchronisieren')
        await nextTick()
        await nextTick()
        expect(triggerSeoSync).toHaveBeenCalledWith(30)
        expect(host.textContent).toContain('Sync gestartet')
    })

    it('zeigt eine Fehlermeldung, wenn die API scheitert', async () => {
        getSeoStatus.mockResolvedValue(status)
        getSeoOverview.mockRejectedValue(new Error('500'))
        const host = await mount()
        expect(host.textContent).toContain('konnten nicht geladen werden')
    })
})
