// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SeoAuditView, SeoOpportunities, SeoCampaignRow, SeoModelRow, SeoOverview, SeoPageRow, SeoQueryRow, SeoStatus } from '../../../api/seoService'

const getSeoStatus = vi.fn()
const getSeoOverview = vi.fn()
const getSeoPages = vi.fn()
const getSeoQueries = vi.fn()
const getSeoCampaigns = vi.fn()
const getSeoModels = vi.fn()
const triggerSeoSync = vi.fn()
const getSeoAudit = vi.fn()
const triggerSeoAudit = vi.fn()
const getSeoOpportunities = vi.fn()
const getSeoPageQueries = vi.fn()
const getSeoQueryPages = vi.fn()
const getSeoBrandTrend = vi.fn()
const getSeoCompetition = vi.fn()
const getSeoKeywordMetrics = vi.fn()
const triggerSeoCompetition = vi.fn()
vi.mock('../../../api/seoService', () => ({
    getSeoCompetition: (d: number) => getSeoCompetition(d),
    getSeoKeywordMetrics: () => getSeoKeywordMetrics(),
    triggerSeoCompetition: () => triggerSeoCompetition(),
    getSeoAudit: () => getSeoAudit(),
    triggerSeoAudit: () => triggerSeoAudit(),
    getSeoOpportunities: (d: number, m: string) => getSeoOpportunities(d, m),
    getSeoPageQueries: (d: number, p: string) => getSeoPageQueries(d, p),
    getSeoQueryPages: (d: number, q: string) => getSeoQueryPages(d, q),
    getSeoBrandTrend: (w: number) => getSeoBrandTrend(w),
    getSeoStatus: () => getSeoStatus(),
    getSeoOverview: (d: number, m: string) => getSeoOverview(d, m),
    getSeoPages: (d: number, f: string, m: string) => getSeoPages(d, f, m),
    getSeoQueries: (d: number, b: string, f: string, m: string) => getSeoQueries(d, b, f, m),
    getSeoCampaigns: (d: number) => getSeoCampaigns(d),
    getSeoModels: (d: number, l: string, f: string) => getSeoModels(d, l, f),
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
    periodDays: 28, from: '2026-09-19', to: '2026-10-16', market: 'DE', previousComplete: true,
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
const models: SeoModelRow[] = [
    { brand: 'Alpha', model: 'One', path: '/modelle/Alpha/One', impressions: 41300, clicks: 1820, ctr: 0.044, avgPosition: 6.1, registrations: 9, cars: 40, logs: 900, finding: null },
    { brand: 'Beta', model: 'Two', path: '/modelle/Beta/Two', impressions: 6000, clicks: 10, ctr: 0.0017, avgPosition: 25, registrations: 0, cars: 0, logs: 0, finding: 'CONTENT_GAP' },
    { brand: 'Gamma', model: 'Three', path: '/modelle/Gamma/Three', impressions: 0, clicks: 0, ctr: 0, avgPosition: 0, registrations: 0, cars: 12, logs: 300, finding: 'INVISIBLE' },
]

const noOpportunities: SeoOpportunities = { underrated: 0, leak: 0, dead: 0, money: 0, striking: 0, cannibalized: 0, adwords: 0, contentGap: 0, invisible: 0 }
const emptyAudit: SeoAuditView = { enabled: false, day: null, lastRunAt: null, vitalsWeek: null,
    summary: { checked: 0, withErrors: 0, prerenderFailed: 0, brokenLinkPages: 0, orphans: 0 }, rows: [] }

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
    vi.resetAllMocks()
    localStorage.clear()
})

function defaults() {
    getSeoStatus.mockResolvedValue(status)
    getSeoOverview.mockResolvedValue(overview)
    getSeoOpportunities.mockResolvedValue(noOpportunities)
    getSeoAudit.mockResolvedValue(emptyAudit)
    getSeoBrandTrend.mockResolvedValue([])
    getSeoPageQueries.mockResolvedValue([])
    getSeoQueryPages.mockResolvedValue([])
    getSeoKeywordMetrics.mockResolvedValue([])
    getSeoCompetition.mockResolvedValue({ enabled: false, serpWeek: null, metricsMonth: null, costThisMonthUsd: 0, monthlyBudgetUsd: 5,
        competitors: [], queries: [], ideas: [] })
}

async function flush(n = 6) {
    for (let i = 0; i < n; i++) await nextTick()
    await new Promise(r => setTimeout(r, 0))
    for (let i = 0; i < 2; i++) await nextTick()
}

async function mount(): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AdminSeoTab)
    app.mount(host)
    await flush()
    return host
}

function click(host: HTMLElement, label: string) {
    const btn = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === label || b.getAttribute('aria-label') === label)
    if (!btn) throw new Error(`Button ${label} fehlt`)
    btn.click()
}

const cells = (host: HTMLElement) => [...host.querySelectorAll('tbody > tr')]
    .map(r => [...r.querySelectorAll(':scope > td')].map(td => td.textContent?.trim()))

describe('AdminSeoTab', () => {
    it('lädt den Überblick für Deutschland und 28 Tage, gruppiert Kacheln und zeigt den Datenstand', async () => {
        defaults()
        const host = await mount()
        const text = host.textContent ?? ''

        expect(getSeoOverview).toHaveBeenCalledWith(28, 'DE')
        expect(text).toContain('Datenstand: Google bis 16.10.2026, Besucher bis 17.10.2026')
        expect(text).toContain('Bei Google')
        expect(text).toContain('Auf der Website')
        expect(text).toContain('Ergebnis')
        expect(text).toContain('184.200')
        expect(text).toContain('+12 % zum Vorzeitraum')
        expect(text).toContain('1,1 Plätze besser')
        expect(text).toContain('ganze Website, alle Märkte')
        // Anleitung beim ersten Besuch offen
        expect(text).toContain('Geht es bei Google und auf der Website aufwärts oder abwärts?')
        expect(host.querySelectorAll('.chart').length).toBe(1)
    })

    it('zeigt keine Prozent-Deltas, wenn der Vorzeitraum nicht vollständig ist', async () => {
        defaults()
        getSeoOverview.mockResolvedValue({ ...overview, previousComplete: false })
        const host = await mount()

        expect(host.textContent).toContain('kein vollständiger Vorzeitraum')
        expect(host.textContent).not.toContain('% zum Vorzeitraum')
    })

    it('springt aus der Handlungsliste in die gefilterte Seitenliste mit Erklärung', async () => {
        defaults()
        getSeoOpportunities.mockResolvedValue({ ...noOpportunities, underrated: 3, adwords: 2 })
        getSeoPages.mockResolvedValue(pages)
        const host = await mount()

        expect(host.textContent).toContain('Was du jetzt tun kannst')
        const item = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Unterschätzt'))!
        item.click()
        await flush()

        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'UNDERRATED', 'DE')
        expect(host.textContent).toContain('Was tun:')
    })

    it('Seiten: Markt, Filter, aufklappbare Suchbegriffe und Technik-Spalte', async () => {
        defaults()
        getSeoPages.mockResolvedValue(pages)
        getSeoPageQueries.mockResolvedValue([{ key: 'alpha one test', brand: false, impressions: 900, clicks: 40, ctr: 0.044, avgPosition: 5.2 }])
        getSeoAudit.mockResolvedValue({ ...emptyAudit, enabled: true, day: '2026-10-19',
            rows: [{ path: '/modelle/Alpha/One', httpStatus: 200, score: 75, errors: 1, warnings: 1, vitals: null, findings: [] }] })
        const host = await mount()

        click(host, 'Seiten')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'ALL', 'DE')
        expect(host.textContent).toContain('1 Fehler, 1 Hinweis')
        expect(host.textContent).toContain('Geldseite')

        click(host, 'International')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'ALL', 'INTL')

        click(host, 'Leck')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'LEAK', 'INTL')

        click(host, 'Aufklappen')
        await flush()
        expect(getSeoPageQueries).toHaveBeenCalledWith(28, '/modelle/Alpha/One')
        expect(host.textContent).toContain('alpha one test')
    })

    it('Suchbegriffe: fremder Text bleibt Text, Filter und Markenschalter', async () => {
        defaults()
        getSeoQueries.mockResolvedValue(queries)
        const host = await mount()

        click(host, 'Suchbegriffe')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'ALL', 'ALL', 'DE')
        expect(getSeoBrandTrend).toHaveBeenCalledWith(26)
        expect(host.querySelector('td b')).toBeNull()
        expect(host.textContent).toContain('alpha one verbrauch <b>x</b>')
        expect(host.textContent).toContain('Konkurrenz im eigenen Haus')

        click(host, 'AdWords-Kandidat')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'ALL', 'ADWORDS', 'DE')

        click(host, 'Ohne Markennamen')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'NON_BRAND', 'ADWORDS', 'DE')
    })

    it('Kampagnen: Zuordnung nur mit UTM oder Google, Registrierungen je 1 000 Besucher', async () => {
        defaults()
        getSeoCampaigns.mockResolvedValue(campaigns)
        const host = await mount()

        click(host, '90 Tage')
        await flush()
        click(host, 'Kampagnen')
        await flush()
        expect(getSeoCampaigns).toHaveBeenLastCalledWith(90)
        const rows = cells(host)
        expect(rows[0]).toEqual(['Google', '-', '-', '1.800', '5,0 %', '21', '11,7', '2'])
        expect(rows[1]).toEqual(['Direkt (ohne Verweis)', '-', '-', '900', '4,4 %', '-', '-', '-'])
        expect(rows[2]).toEqual(['vw-forum', 'forum', 'vorstellung', '40', '10,0 %', '3', '75,0', '1'])
        expect(host.textContent).toContain('Kampagnen-Link bauen')
    })

    it('Modellseiten: Befunde und Ebene Marke', async () => {
        defaults()
        getSeoModels.mockResolvedValue(models)
        const host = await mount()

        click(host, 'Modellseiten')
        await flush()
        expect(getSeoModels).toHaveBeenLastCalledWith(28, 'MODEL', 'ALL')
        const rows = cells(host)
        expect(rows[0]).toEqual(['Alpha', 'One', '41.300', '1.820', '4,4 %', '6,1', '9', '40', '900', '-'])
        expect(rows[1]).toEqual(['Beta', 'Two', '6.000', '10', '0,2 %', '25,0', '-', '0', '0', 'Nachfrage ohne Daten'])
        expect(host.querySelector('tbody a')?.getAttribute('href')).toBe('/modelle/Alpha/One')

        click(host, 'Nachfrage ohne Daten')
        await flush()
        expect(getSeoModels).toHaveBeenLastCalledWith(28, 'MODEL', 'CONTENT_GAP')

        click(host, 'Je Marke')
        await flush()
        expect(getSeoModels).toHaveBeenLastCalledWith(28, 'BRAND', 'ALL')
    })

    it('meldet fehlende Verbindungen und einen abgelehnten Abruf verständlich', async () => {
        defaults()
        getSeoStatus.mockResolvedValue({ enabled: false, dataUntil: '2026-10-17', plausibleEnabled: false, plausibleDataUntil: null, runs: [] })
        triggerSeoSync.mockResolvedValue(false)
        const host = await mount()

        expect(host.textContent).toContain('Google Search Console ist nicht verbunden')
        expect(host.textContent).toContain('Plausible ist nicht verbunden')
        const sync = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Daten jetzt abholen')!
        expect(sync.disabled).toBe(true)
    })

    it('startet einen Abruf und zeigt die Rückmeldung', async () => {
        defaults()
        triggerSeoSync.mockResolvedValue(true)
        const host = await mount()

        click(host, 'Daten jetzt abholen')
        await flush()
        expect(triggerSeoSync).toHaveBeenCalledWith(30)
        expect(host.textContent).toContain('im Hintergrund neu abgeholt')
    })

    it('zeigt eine Fehlermeldung, wenn die API scheitert', async () => {
        defaults()
        getSeoOverview.mockRejectedValue(new Error('500'))
        const host = await mount()
        expect(host.textContent).toContain('konnte nicht geladen werden')
    })

    it('Technik: Kacheln, Filter, Befund-Texte als Text, Prüfung starten', async () => {
        const audit: SeoAuditView = {
            enabled: true, day: '2026-10-19', lastRunAt: '2026-10-19T05:00:00', vitalsWeek: null,
            summary: { checked: 2, withErrors: 1, prerenderFailed: 1, brokenLinkPages: 0, orphans: 0 },
            rows: [
                { path: '/modelle/Alpha/One', httpStatus: 200, score: 55, errors: 2, warnings: 1, vitals: null, findings: [
                    { check: 'PRERENDER', severity: 'ERROR', message: 'US-Einheit oder Dollar im HTML: "mi/kWh"' },
                    { check: 'JSONLD', severity: 'ERROR', message: 'JSON-LD Product fehlt' },
                    { check: 'TITLE', severity: 'WARNING', message: 'Title hat 76 Zeichen: <b>fett</b>' }] },
                { path: '/modelle/Beta/Two', httpStatus: 200, score: 100, errors: 0, warnings: 0, vitals: null, findings: [] },
            ],
        }
        defaults()
        getSeoAudit.mockResolvedValue(audit)
        triggerSeoAudit.mockResolvedValue(false)
        const host = await mount()

        click(host, 'Technik')
        await flush()
        expect(host.textContent).toContain('Seiten geprüft')
        expect(host.textContent).toContain('Fehler in der Google-Fassung')

        click(host, 'Google-Fassung')
        await flush()
        expect(host.textContent).not.toContain('/modelle/Beta/Two')

        click(host, 'Aufklappen')
        await flush()
        expect(host.textContent).toContain('JSON-LD Product fehlt')
        expect(host.textContent).toContain('<b>fett</b>')
        expect(host.querySelector('tbody b')).toBeNull()

        click(host, 'Jetzt prüfen')
        await flush()
        expect(triggerSeoAudit).toHaveBeenCalled()
        expect(host.textContent).toContain('läuft gerade schon eine Prüfung')
    })

    it('Suchbegriffe: Suchvolumen, Sichtbarkeit und CPC, sobald Wettbewerbsdaten da sind', async () => {
        defaults()
        getSeoQueries.mockResolvedValue(queries)
        getSeoKeywordMetrics.mockResolvedValue([{ query: 'alpha one verbrauch <b>x</b>', volume: 20000, cpc: 0.84, competition: 'LOW' }])
        const host = await mount()

        click(host, 'Suchbegriffe')
        await flush()
        const header = [...host.querySelectorAll('thead th')].map(th => th.textContent?.trim())
        expect(header).toContain('Suchvolumen')
        expect(header).toContain('Sichtbarkeit')
        // 12 400 Impressionen in 28 Tagen = 13 286 im Monat, bei 20 000 Suchen 66 %
        expect(cells(host)[0]).toContain('66 %')
        expect(cells(host)[0]).toContain('0,84 US$')
    })

    it('Wettbewerb: Einrichtungshinweis ohne Zugang', async () => {
        defaults()
        const host = await mount()

        click(host, 'Wettbewerb')
        await flush()
        expect(getSeoCompetition).toHaveBeenCalledWith(28)
        expect(host.textContent).toContain('noch nicht eingerichtet')
        expect(host.textContent).toContain('DATAFORSEO_LOGIN')
    })

    it('Wettbewerb: Wettbewerber, unsere Plätze mit Top 10, Ideen nur neue, fremde Links nur http(s)', async () => {
        defaults()
        getSeoCompetition.mockResolvedValue({
            enabled: true, serpWeek: '2026-10-19', metricsMonth: '2026-10-01', costThisMonthUsd: 0.12, monthlyBudgetUsd: 5,
            competitors: [{ domain: 'wettbewerb.test', queries: 2, aheadOfUs: 2, avgRank: 1, bestRank: 1 }],
            queries: [{ query: 'testauto reichweite', impressions: 900, ourRank: 2, ourUrl: 'https://ev-monitor.net/modelle/A', volume: 1300, cpc: 0.84,
                top: [{ rank: 1, domain: 'wettbewerb.test', url: 'javascript:alert(1)', title: '<i>Fremd</i>', own: false },
                    { rank: 2, domain: 'ev-monitor.net', url: 'https://ev-monitor.net/modelle/A', title: 'Wir', own: true }] }],
            ideas: [{ keyword: 'testauto ladezeit', seed: 'testauto reichweite', volume: 400, cpc: 0.5, covered: false },
                { keyword: 'testauto reichweite', seed: 'testauto reichweite', volume: 1300, cpc: 0.84, covered: true }],
        })
        triggerSeoCompetition.mockResolvedValue(true)
        const host = await mount()

        click(host, 'Wettbewerb')
        await flush()
        expect(host.textContent).toContain('Kosten diesen Monat: 0,12 US$ von 5,00 US$ Budget')
        expect(host.textContent).toContain('wettbewerb.test')
        expect(host.textContent).toContain('testauto ladezeit')

        const ideaRows = cells(host).filter(r => r.includes('testauto ladezeit') || r.includes('nein') || r.includes('ja'))
        expect(ideaRows.some(r => r.includes('ja'))).toBe(false)

        const expand = [...host.querySelectorAll('button')].filter(b => b.getAttribute('aria-label') === 'Aufklappen')
        expand[0].click()
        await flush()
        expect(host.textContent).toContain('<i>Fremd</i>')
        expect(host.querySelector('tbody i')).toBeNull()
        const foreign = [...host.querySelectorAll('ol a')].find(a => a.textContent?.includes('Fremd'))!
        expect(foreign.getAttribute('href')).toBeNull()

        click(host, 'Fällige Daten jetzt abrufen')
        await flush()
        expect(triggerSeoCompetition).toHaveBeenCalled()
        expect(host.textContent).toContain('Abruf läuft')
    })
})
