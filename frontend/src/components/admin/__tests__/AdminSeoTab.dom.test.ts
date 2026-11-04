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
    current: { impressions: 184200, clicks: 6410, ctr: 0.0348, avgPosition: 14.2, visitors: 3100, googleEntries: 1800, registrations: 37, organicRegistrations: 21, searchEntries: 2100 },
    previous: { impressions: 164500, clicks: 5930, ctr: 0.036, avgPosition: 15.3, visitors: 2900, googleEntries: 1700, registrations: 30, organicRegistrations: 18, searchEntries: 2000 },
    days: [{ day: '2026-10-15', impressions: 100, clicks: 3, visitors: 110, googleEntries: 60 }, { day: '2026-10-16', impressions: 120, clicks: 4, visitors: 130, googleEntries: 70 }],
    brand: { brandImpressions: 900, brandClicks: 120, nonBrandImpressions: 90000, nonBrandClicks: 360 },
    brandPrevious: { brandImpressions: 800, brandClicks: 100, nonBrandImpressions: 80000, nonBrandClicks: 300 },
}
const pages: SeoPageRow[] = [
    { path: '/modelle/Alpha/One', impressions: 41300, clicks: 1820, ctr: 0.044, avgPosition: 6.1, entries: 1700, bounceRate: 0.21, ctaRate: 0.083, registrations: 9, finding: 'MONEY',
        pageType: 'MODEL', clicksPrevious: 1500, potentialClicks: 0 },
    { path: '/modelle/Beta/Two', impressions: 28900, clicks: 610, ctr: 0.021, avgPosition: 9.8, entries: 0, bounceRate: 0, ctaRate: 0, registrations: 0, finding: 'UNDERRATED',
        pageType: 'MODEL', clicksPrevious: 700, potentialClicks: 113 },
    { path: '/blog/ladeverluste', impressions: 300, clicks: 4, ctr: 0.013, avgPosition: 15.7, entries: 20, bounceRate: 0.5, ctaRate: 0, registrations: 0, finding: null,
        pageType: 'CONTENT', clicksPrevious: 4, potentialClicks: 0 },
    { path: '/modelle/TESLA/MODEL_3', impressions: 12, clicks: 0, ctr: 0, avgPosition: 8, entries: 0, bounceRate: 0, ctaRate: 0, registrations: 0, finding: null,
        pageType: 'MODEL', clicksPrevious: 0, potentialClicks: 0 },
    { path: '/modelle/Tesla/Model_3', impressions: 887, clicks: 6, ctr: 0.007, avgPosition: 7.9, entries: 14, bounceRate: 0.4, ctaRate: 0, registrations: 0, finding: null,
        pageType: 'MODEL', clicksPrevious: 6, potentialClicks: 21 },
]
const queries: SeoQueryRow[] = [
    { query: 'alpha one verbrauch <b>x</b>', brand: false, impressions: 12400, clicks: 980, ctr: 0.079, avgPosition: 3.2,
        topPath: '/modelle/Alpha/One', pageCount: 2, adwordsCandidate: true, finding: 'CANNIBALIZED', clicksPrevious: 900, potentialClicks: 0 },
    { query: 'beta two reichweite', brand: false, impressions: 500, clicks: 1, ctr: 0.002, avgPosition: 9.7,
        topPath: '/modelle/Beta/Two', pageCount: 1, adwordsCandidate: false, finding: 'STRIKING_DISTANCE', clicksPrevious: 3, potentialClicks: 54 },
    { query: 'ev monitor', brand: true, impressions: 480, clicks: 32, ctr: 0.066, avgPosition: 6, topPath: '/', pageCount: 10,
        adwordsCandidate: false, finding: null, clicksPrevious: 30, potentialClicks: 0 },
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

const noOpportunities: SeoOpportunities = { underrated: 0, leak: 0, dead: 0, money: 0, striking: 0, cannibalized: 0, adwords: 0, contentGap: 0, invisible: 0, tasks: [] }
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
    getSeoPages.mockResolvedValue([])
    getSeoCampaigns.mockResolvedValue([])
    getSeoQueries.mockResolvedValue([])
    getSeoModels.mockResolvedValue([])
    getSeoBrandTrend.mockResolvedValue([])
    getSeoPageQueries.mockResolvedValue([])
    getSeoQueryPages.mockResolvedValue([])
    getSeoKeywordMetrics.mockResolvedValue([])
    getSeoCompetition.mockResolvedValue({ enabled: false, serpWeek: null, metricsMonth: null, costThisMonthUsd: 0, monthlyBudgetUsd: 5, lastRun: null,
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

const rowsOf = (rows: Element[]) => rows.map(r => [...r.querySelectorAll(':scope > td')].map(td => td.textContent?.trim()))
const cells = (host: HTMLElement) => rowsOf([...host.querySelectorAll('tbody > tr')])
/** Zeilen der letzten Tabelle, wenn eine Ansicht mehrere Tabellen hat. */
const lastTableCells = (host: HTMLElement) => {
    const tables = [...host.querySelectorAll('table')]
    return rowsOf([...tables[tables.length - 1].querySelectorAll('tbody > tr')])
}

/** Öffnet "Datenquellen und Abrufe"; das Panel lädt Crawl- und SERP-Stand erst beim Öffnen. */
async function openSources(host: HTMLElement) {
    const details = [...host.querySelectorAll('details')].find(d => d.querySelector('summary')?.textContent?.includes('Datenquellen'))!
    details.open = true
    details.dispatchEvent(new Event('toggle'))
    await flush()
}

describe('AdminSeoTab', () => {
    it('Übersicht: Deutschland, 28 Tage, Kacheln nach Quelle, Brand/Non-Brand und Datenstand', async () => {
        defaults()
        const host = await mount()
        const text = host.textContent ?? ''

        expect(getSeoOverview).toHaveBeenCalledWith(28, 'DE')
        expect(text).toContain('Datenstand: Search Console bis 16.10.2026, Plausible bis 17.10.2026')
        expect(text).toContain('Search Performance')
        expect(text).toContain('Traffic')
        expect(text).toContain('Conversions')
        expect(text).toContain('184.200')
        expect(text).toContain('+12 % zum Vorzeitraum')
        expect(text).toContain('1,1 Plätze besser')
        expect(text).toContain('ganze Website, alle Märkte')
        // Brand / Non-Brand: 360 statt 300 Non-Brand-Klicks = +20 %
        expect(text).toContain('Non-Brand-Klicks')
        expect(text).toContain('+20 % zum Vorzeitraum')
        // Organic Conversion Rate über alle Suchmaschinen: 21 / 2 100
        expect(text).toContain('Organische Sitzungen (alle Suchmaschinen)')
        expect(text).toContain('2.100')
        expect(text).toContain('Organic Conversion Rate')
        expect(text).toContain('1,0 %')
        // kein Markt-Schalter mehr
        expect(text).not.toContain('International')
        expect(text).toContain('Was ist diese Woche zu tun')
        expect(host.querySelectorAll('.chart').length).toBe(1)
    })

    it('zeigt keine Prozent-Deltas, wenn der Vorzeitraum nicht vollständig ist', async () => {
        defaults()
        getSeoOverview.mockResolvedValue({ ...overview, previousComplete: false })
        const host = await mount()

        expect(host.textContent).toContain('kein vollständiger Vorzeitraum')
        expect(host.textContent).not.toContain('% zum Vorzeitraum')
    })

    it('Top-Aufgaben nach Klick-Potenzial springen mit Suche in die passende Liste', async () => {
        defaults()
        getSeoOpportunities.mockResolvedValue({ ...noOpportunities, striking: 1, underrated: 1, tasks: [
            { type: 'STRIKING_DISTANCE', key: 'beta two reichweite', path: '/modelle/Beta/Two', potentialClicks: 54 },
            { type: 'UNDERRATED', key: '/modelle/Beta/Two', path: '/modelle/Beta/Two', potentialClicks: 27 }] })
        getSeoQueries.mockResolvedValue(queries)
        const host = await mount()

        expect(host.textContent).toContain('Top-Aufgaben')
        expect(host.textContent).toContain('+54')
        const task = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Striking Distance: beta two reichweite'))!
        task.click()
        await flush()

        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'ALL', 'STRIKING_DISTANCE', 'DE')
        expect((host.querySelector('input[type=search]') as HTMLInputElement).value).toBe('beta two reichweite')
    })

    it('Gewinner und Verlierer nach Klicks, Channels mit Sign-ups je 1 000 Besucher', async () => {
        defaults()
        getSeoPages.mockResolvedValue(pages)
        getSeoCampaigns.mockResolvedValue(campaigns)
        const host = await mount()
        const text = host.textContent ?? ''

        expect(text).toContain('Gewinner und Verlierer')
        expect(text).toContain('+320')
        expect(text).toContain('1.820 statt 1.500')
        expect(text).toContain('-90')
        expect(text).toContain('Organic Search')
        expect(text).toContain('Referral: Foren')
    })

    it('Alle Befunde springen in die gefilterte Landingpage-Liste mit Erklärung', async () => {
        defaults()
        getSeoOpportunities.mockResolvedValue({ ...noOpportunities, underrated: 3, adwords: 2 })
        getSeoPages.mockResolvedValue(pages)
        const host = await mount()

        expect(host.textContent).toContain('Alle Befunde')
        const item = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Low CTR'))!
        item.click()
        await flush()

        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'UNDERRATED', 'DE')
        expect(host.textContent).toContain('Was tun:')
    })

    it('Landingpages: Seitentyp, Befundfilter, Δ Klicks, Potenzial, Keywords aufklappen, Site-Audit-Spalte', async () => {
        defaults()
        getSeoPages.mockResolvedValue(pages)
        getSeoPageQueries.mockResolvedValue([{ key: 'alpha one test', brand: false, impressions: 900, clicks: 40, ctr: 0.044, avgPosition: 5.2 }])
        getSeoAudit.mockResolvedValue({ ...emptyAudit, enabled: true, day: '2026-10-19',
            rows: [{ path: '/modelle/Alpha/One', httpStatus: 200, score: 75, errors: 1, warnings: 1, vitals: null, findings: [] }] })
        const host = await mount()

        click(host, 'Landingpages')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'ALL', 'DE')
        expect(host.textContent).toContain('1 Fehler, 1 Hinweis')
        expect(host.textContent).toContain('Top Converter')
        const header = [...host.querySelectorAll('thead th')].map(th => th.textContent?.replace(/\s+/g, ' ').trim())
        expect(header).toContain('ImpressionenGSC')
        expect(header).toContain('EinstiegePlausible')
        expect(header).toContain('Sign-upsDB')
        expect(cells(host)[0]).toContain('+320')

        click(host, 'Content')
        await flush()
        expect(cells(host).map(r => r[1])).toEqual(['/blog/ladeverluste'])
        click(host, 'Alle Seitentypen')
        await flush()

        click(host, 'High Bounce')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(28, 'LEAK', 'DE')

        click(host, 'Aufklappen')
        await flush()
        expect(getSeoPageQueries).toHaveBeenCalledWith(28, '/modelle/Alpha/One')
        expect(host.textContent).toContain('alpha one test')
    })

    it('Keywords: fremder Text bleibt Text, Cluster, Filter und Brand-Schalter', async () => {
        defaults()
        getSeoQueries.mockResolvedValue(queries)
        const host = await mount()

        click(host, 'Keywords')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'ALL', 'ALL', 'DE')
        expect(getSeoBrandTrend).toHaveBeenCalledWith(26)
        expect(host.querySelector('td b')).toBeNull()
        expect(host.textContent).toContain('alpha one verbrauch <b>x</b>')
        expect(host.textContent).toContain('Keyword-Kannibalisierung')
        expect(host.textContent).toContain('Keyword-Cluster')

        const reichweite = [...host.querySelectorAll('button')].find(b => b.textContent?.startsWith('Reichweite'))!
        reichweite.click()
        await flush()
        expect(cells(host).map(r => r[1])).toEqual(['beta two reichweite'])

        click(host, 'SEA-Kandidat')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'ALL', 'ADWORDS', 'DE')

        click(host, 'Non-Brand')
        await flush()
        expect(getSeoQueries).toHaveBeenLastCalledWith(28, 'NON_BRAND', 'ADWORDS', 'DE')
    })

    it('Akquisition: Channels, Zuordnung nur mit UTM oder Google, Sign-ups je 1 000 Besucher', async () => {
        defaults()
        getSeoCampaigns.mockResolvedValue(campaigns)
        const host = await mount()

        click(host, '90 Tage')
        await flush()
        click(host, 'Akquisition')
        await flush()
        expect(getSeoCampaigns).toHaveBeenLastCalledWith(90)
        expect(host.textContent).toContain('Channels')
        const rows = lastTableCells(host)
        expect(rows[0]).toEqual(['Google', 'Organic Search', '-', '-', '1.800', '5,0 %', '21', '11,7', '2'])
        expect(rows[1]).toEqual(['Direkt (ohne Verweis)', 'Direct', '-', '-', '900', '4,4 %', '-', '-', '-'])
        expect(rows[2]).toEqual(['vw-forum', 'Referral: Foren', 'forum', 'vorstellung', '40', '10,0 %', '3', '75,0', '1'])
        expect(host.textContent).toContain('UTM-Builder')
    })

    it('Landingpages: Modellseiten mit Datenbasis, Befunde und Ebene Marke', async () => {
        defaults()
        getSeoModels.mockResolvedValue(models)
        const host = await mount()

        click(host, 'Landingpages')
        await flush()
        click(host, 'Modellseiten: Nachfrage und Datenbasis')
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

    it('Befund "Nachfrage ohne Daten" springt direkt in die Modell-Datenbasis', async () => {
        defaults()
        getSeoOpportunities.mockResolvedValue({ ...noOpportunities, contentGap: 2 })
        getSeoModels.mockResolvedValue(models)
        const host = await mount()

        const item = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Nachfrage ohne Daten'))!
        item.click()
        await flush()
        expect(getSeoModels).toHaveBeenLastCalledWith(28, 'MODEL', 'CONTENT_GAP')
    })

    it('Datenquellen: fehlende Verbindungen, Links zu den Tools, abgelehnter Abruf deaktiviert', async () => {
        defaults()
        getSeoStatus.mockResolvedValue({ enabled: false, dataUntil: '2026-10-17', plausibleEnabled: false, plausibleDataUntil: null, runs: [] })
        const host = await mount()

        expect(host.textContent).toContain('Nicht verbunden. Admin: GSC_SERVICE_ACCOUNT_JSON setzen.')
        expect(host.textContent).toContain('Nicht verbunden. Admin: PLAUSIBLE_API_KEY setzen.')
        const links = [...host.querySelectorAll('a')].map(a => a.getAttribute('href'))
        expect(links).toContain('https://plausible.io/ev-monitor.net')
        expect(links.some(l => l?.startsWith('https://search.google.com/search-console'))).toBe(true)
        const sync = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === 'GSC und Plausible jetzt abrufen')!
        expect(sync.disabled).toBe(true)
    })

    it('Datenquellen: Abruf starten und Rückmeldung, fehlgeschlagener Abruf oben sichtbar', async () => {
        defaults()
        getSeoStatus.mockResolvedValue({ ...status, runs: [{ ...status.runs[0], status: 'FAILED', error: 'HTTP 403' }] })
        triggerSeoSync.mockResolvedValue(true)
        const host = await mount()

        expect(host.textContent).toContain('Der letzte Abruf von Search Console ist fehlgeschlagen')
        click(host, 'GSC und Plausible jetzt abrufen')
        await flush()
        expect(triggerSeoSync).toHaveBeenCalledWith(30)
        expect(host.textContent).toContain('werden neu abgerufen')
    })

    it('zeigt eine Fehlermeldung, wenn die API scheitert', async () => {
        defaults()
        getSeoOverview.mockRejectedValue(new Error('500'))
        const host = await mount()
        expect(host.textContent).toContain('konnte nicht geladen werden')
    })

    it('Site Audit: Kacheln, Filter, Befund-Texte als Text, Duplicate URLs, Crawl unter Datenquellen', async () => {
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
        getSeoPages.mockResolvedValue(pages)
        triggerSeoAudit.mockResolvedValue(false)
        const host = await mount()

        click(host, 'Site Audit')
        await flush()
        expect(getSeoPages).toHaveBeenLastCalledWith(90, 'ALL', 'DE')
        expect(host.textContent).toContain('Seiten gecrawlt')
        expect(host.textContent).toContain('Prerendering-Fehler')
        expect(host.textContent).toContain('Duplicate URLs')
        expect(host.textContent).toContain('/modelle/TESLA/MODEL_3')

        click(host, 'Prerendering')
        await flush()
        expect(host.textContent).not.toContain('/modelle/Beta/Two')

        click(host, 'Aufklappen')
        await flush()
        expect(host.textContent).toContain('JSON-LD Product fehlt')
        expect(host.textContent).toContain('<b>fett</b>')
        expect(host.querySelector('tbody b')).toBeNull()

        await openSources(host)
        click(host, 'Crawl jetzt starten')
        await flush()
        expect(triggerSeoAudit).toHaveBeenCalled()
        expect(host.textContent).toContain('Es läuft schon ein Crawl')
    })

    it('Keywords: Suchvolumen, Impression Share und CPC, sobald SERP-Daten da sind', async () => {
        defaults()
        getSeoQueries.mockResolvedValue(queries.slice(0, 1))
        getSeoKeywordMetrics.mockResolvedValue([{ query: 'alpha one verbrauch <b>x</b>', volume: 20000, cpc: 0.84, competition: 'LOW' }])
        const host = await mount()

        click(host, 'Keywords')
        await flush()
        const header = [...host.querySelectorAll('thead th')].map(th => th.textContent?.replace(/\s+/g, ' ').trim())
        expect(header).toContain('SuchvolumenDataForSEO')
        expect(header).toContain('Impression Share')
        // 12 400 Impressionen in 28 Tagen = 13 286 im Monat, bei 20 000 Suchen 66 %
        expect(cells(host)[0]).toContain('66 %')
        expect(cells(host)[0]).toContain('0,84 US$')
    })

    it('SERP & Wettbewerb: Hinweis ohne Zugang, Einrichtung unter Datenquellen', async () => {
        defaults()
        const host = await mount()

        click(host, 'SERP & Wettbewerb')
        await flush()
        expect(getSeoCompetition).toHaveBeenCalledWith(28)
        expect(host.textContent).toContain('nicht angebunden')
        await openSources(host)
        expect(host.textContent).toContain('DATAFORSEO_LOGIN')
    })

    it('SERP & Wettbewerb: SERP je Keyword mit Top 10, Ideen nur neue, Domains, fremde Links nur http(s)', async () => {
        defaults()
        getSeoCompetition.mockResolvedValue({
            enabled: true, serpWeek: '2026-10-19', metricsMonth: '2026-10-01', costThisMonthUsd: 0.12, monthlyBudgetUsd: 5, lastRun: null,
            competitors: [{ domain: 'wettbewerb.test', queries: 2, aheadOfUs: 2, avgRank: 1, bestRank: 1 }],
            queries: [{ query: 'testauto reichweite', impressions: 900, ourRank: 2, ourUrl: 'https://ev-monitor.net/modelle/A', volume: 1300, cpc: 0.84,
                top: [{ rank: 1, domain: 'wettbewerb.test', url: 'javascript:alert(1)', title: '<i>Fremd</i>', own: false },
                    { rank: 2, domain: 'ev-monitor.net', url: 'https://ev-monitor.net/modelle/A', title: 'Wir', own: true }] }],
            ideas: [{ keyword: 'testauto ladezeit', seed: 'testauto reichweite', volume: 400, cpc: 0.5, covered: false },
                { keyword: 'testauto reichweite', seed: 'testauto reichweite', volume: 1300, cpc: 0.84, covered: true }],
        })
        triggerSeoCompetition.mockResolvedValue(true)
        const host = await mount()

        click(host, 'SERP & Wettbewerb')
        await flush()
        expect(host.textContent).toContain('SERP je Keyword')
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

        await openSources(host)
        expect(host.textContent).toContain('Kosten diesen Monat: 0,12 US$ von 5,00 US$ Budget')
        click(host, 'Fällige SERP-Daten abrufen')
        await flush()
        expect(triggerSeoCompetition).toHaveBeenCalled()
        expect(host.textContent).toContain('SERP-Abruf läuft')
    })

    it('SERP & Wettbewerb: fehlgeschlagener Abruf, Erklärung unter Datenquellen', async () => {
        defaults()
        getSeoCompetition.mockResolvedValue({ enabled: true, serpWeek: null, metricsMonth: null, costThisMonthUsd: 0, monthlyBudgetUsd: 5,
            lastRun: { startedAt: '2026-10-20T07:16:22', status: 'FAILED', message: '/serp: HTTP 403 (40104 Please verify your account before using the API.)' },
            competitors: [], queries: [], ideas: [] })
        const host = await mount()

        click(host, 'SERP & Wettbewerb')
        await flush()
        expect(host.textContent).toContain('Der letzte SERP-Abruf ist fehlgeschlagen')
        await openSources(host)
        expect(host.textContent).toContain('ist fehlgeschlagen')
        expect(host.textContent).toContain('noch nicht verifiziert')
        expect(host.textContent).toContain('40104')
    })
})
