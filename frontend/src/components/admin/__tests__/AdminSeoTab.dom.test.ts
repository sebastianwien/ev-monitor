// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SeoCampaignRow, SeoOverview, SeoPageRow, SeoQueryRow, SeoStatus } from '../../../api/seoService'

const getSeoStatus = vi.fn()
const getSeoOverview = vi.fn()
const getSeoPages = vi.fn()
const getSeoQueries = vi.fn()
const getSeoCampaigns = vi.fn()
const triggerSeoSync = vi.fn()
vi.mock('../../../api/seoService', () => ({
    getSeoStatus: () => getSeoStatus(),
    getSeoOverview: (d: number) => getSeoOverview(d),
    getSeoPages: (d: number, f: string) => getSeoPages(d, f),
    getSeoQueries: (d: number, b: string, f: string) => getSeoQueries(d, b, f),
    getSeoCampaigns: (d: number) => getSeoCampaigns(d),
    triggerSeoSync: (d: number) => triggerSeoSync(d),
}))
// Chart.js braucht Canvas; in jsdom reicht ein Platzhalter
vi.mock('vue-chartjs', () => ({ Line: { template: '<div class="chart" />' } }))

import AdminSeoTab from '../AdminSeoTab.vue'

const status: SeoStatus = {
    enabled: true,
    dataUntil: '2026-10-16',
    plausibleEnabled: true,
    plausibleDataUntil: '2026-10-17',
    runs: [{ id: 'r1', source: 'GSC', startedAt: '2026-10-19T04:30:00', finishedAt: '2026-10-19T04:31:00', status: 'SUCCESS',
        rowCount: 1234, fromDay: '2026-10-10', toDay: '2026-10-16', error: null },
        { id: 'r2', source: 'PLAUSIBLE', startedAt: '2026-10-19T04:32:00', finishedAt: '2026-10-19T04:33:00', status: 'SUCCESS',
        rowCount: 567, fromDay: '2026-10-11', toDay: '2026-10-17', error: null }],
}
const overview: SeoOverview = {
    periodDays: 28, from: '2026-09-19', to: '2026-10-16',
    current: { impressions: 184200, clicks: 6410, ctr: 0.0348, avgPosition: 14.2, visitors: 3100, googleEntries: 1800, registrations: 37, organicRegistrations: 21 },
    previous: { impressions: 164500, clicks: 5930, ctr: 0.036, avgPosition: 15.3, visitors: 2900, googleEntries: 1700, registrations: 30, organicRegistrations: 18 },
    days: [{ day: '2026-10-15', impressions: 100, clicks: 3, visitors: 110, googleEntries: 60 }, { day: '2026-10-16', impressions: 120, clicks: 4, visitors: 130, googleEntries: 70 }],
}
const pages: SeoPageRow[] = [
    { path: '/modelle/Alpha/One', impressions: 41300, clicks: 1820, ctr: 0.044, avgPosition: 6.1, entries: 1700, bounceRate: 0.21, ctaRate: 0.083, registrations: 9, finding: 'MONEY' },
    { path: '/modelle/Beta/Two', impressions: 28900, clicks: 610, ctr: 0.021, avgPosition: 9.8, entries: 0, bounceRate: 0, ctaRate: 0, registrations: 0, finding: 'UNDERRATED' },
]
const queries: SeoQueryRow[] = [
    { query: 'alpha one verbrauch <b>x</b>', brand: false, impressions: 12400, clicks: 980, ctr: 0.079, avgPosition: 3.2,
        topPath: '/modelle/Alpha/One', pageCount: 2, adwordsCandidate: true, finding: 'CANNIBALIZED' },
]
const campaigns: SeoCampaignRow[] = [
    { source: 'Google', utmMedium: '', utmCampaign: '', visitors: 1800, visits: 1900, ctaVisitors: 90, ctaRate: 0.05, registrations: 21, trials: 2, attributed: true },
    { source: 'Direct / None', utmMedium: '', utmCampaign: '', visitors: 900, visits: 950, ctaVisitors: 40, ctaRate: 0.044, registrations: 0, trials: 0, attributed: false },
    { source: 'vw-forum', utmMedium: 'forum', utmCampaign: 'vorstellung', visitors: 40, visits: 44, ctaVisitors: 4, ctaRate: 0.1, registrations: 3, trials: 1, attributed: true },
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
        expect(text).toContain('Registrierungen')
        expect(text).toContain('37')
        expect(text).toContain('+23 % zum Vorzeitraum')
        expect(text).toContain('0,2')
        expect(text).toContain('1.234 Zeilen')
        expect(text).toContain('Plausible bis 17.10.2026')
        expect(text).toContain('567 Zeilen')
        expect(text).toContain('Besucher')
        expect(text).toContain('3.100')
        expect(text).toContain('1.800')
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
        expect(host.textContent).toContain('Reg.')
        expect(host.querySelector('td.text-emerald-300')?.textContent?.trim()).toBe('9')
        expect(host.textContent).toContain('Einstiege')
        expect(host.textContent).toContain('1.700')
        expect(host.textContent).toContain('21 %')
        expect(host.textContent).toContain('8,3 %')
        expect(host.textContent).toContain('Geldseite')

        click(host, 'Leck')
        await nextTick()
        expect(getSeoPages).toHaveBeenLastCalledWith(90, 'LEAK')

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
        expect(host.textContent).toContain('AdWords')

        click(host, 'AdWords-Kandidaten')
        await nextTick()
        expect(getSeoQueries).toHaveBeenLastCalledWith(90, 'ALL', 'ADWORDS')

        click(host, 'Non-Brand')
        await nextTick()
        expect(getSeoQueries).toHaveBeenLastCalledWith(90, 'NON_BRAND', 'ADWORDS')

        getSeoCampaigns.mockResolvedValue(campaigns)
        click(host, 'Kampagnen')
        await nextTick()
        await nextTick()
        expect(getSeoCampaigns).toHaveBeenLastCalledWith(90)
        const rows = [...host.querySelectorAll('tbody tr')].map(r => [...r.querySelectorAll('td')].map(td => td.textContent?.trim()))
        expect(rows[0]).toEqual(['Google', '-', '-', '1.800', '5,0 %', '21', '2'])
        // ohne UTM und nicht Google: keine DB-Zuordnung
        expect(rows[1]).toEqual(['Direct / None', '-', '-', '900', '4,4 %', '-', '-'])
        expect(rows[2]).toEqual(['vw-forum', 'forum', 'vorstellung', '40', '10,0 %', '3', '1'])
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
