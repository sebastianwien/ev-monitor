import api from './axios'

/** Ein gepflegter Tarif (charging_provider_tariffs), wie der Admin-Tab "Ladekarten" ihn zeigt. */
export interface TariffRow {
  id: string
  empName: string
  tariffVariant: string | null
  /** Gilt nur bei diesem Betreiber; null mit Tier: über das Tier-Mapping, ohne beides: eigenes Netz. */
  cpoName: string | null
  priceTier: 'LOW' | 'STANDARD' | 'HIGH' | null
  chargingType: 'AC' | 'DC'
  pricePerKwh: number
  sessionFeeEur: number
  monthlyFeeEur: number
  blockingFeePerMin: number | null
  blockingFeeAfterMin: number | null
  dynamicPricing: boolean
  validFrom: string
  validUntil: string | null
  sourceUrl: string | null
  lastVerifiedAt: string
  /** Gilt an allen Betreibern, für die der Anbieter keinen genaueren Preis hat (Roaming-Preis). */
  appliesElsewhere: boolean
  /** Nur DC: gilt für Ladungen mit mehr als so viel kW; null = ab 0 kW. */
  minPowerKw: number | null
  /** Höchstbetrag der Blockiergebühr je Ladung. */
  blockingFeeCapEur: number | null
}

export interface TariffOverview {
  tariffs: TariffRow[]
  /** Anbieter-Katalog (emp) für die Auswahl, neue Anbieter legt der Server an. */
  emps: string[]
  /** Betreiber-Katalog (charging_networks). */
  cpos: string[]
}

export type TariffCommand = Omit<TariffRow, 'id' | 'lastVerifiedAt'>

export const adminChargingTariffService = {
  async overview(): Promise<TariffOverview> {
    return (await api.get<TariffOverview>('/admin/charging-tariffs')).data
  },
  async create(cmd: TariffCommand): Promise<TariffRow> {
    return (await api.post<TariffRow>('/admin/charging-tariffs', cmd)).data
  },
  async update(id: string, cmd: TariffCommand): Promise<TariffRow> {
    return (await api.put<TariffRow>(`/admin/charging-tariffs/${id}`, cmd)).data
  },
  /** Seite geprüft, Werte stimmen: nur der Stand wird heute. */
  async verify(id: string): Promise<TariffRow> {
    return (await api.post<TariffRow>(`/admin/charging-tariffs/${id}/verify`)).data
  },
  /** validUntil: letzter Tag, an dem der Tarif noch gilt (ISO-Datum). */
  async end(id: string, validUntil: string): Promise<TariffRow> {
    return (await api.post<TariffRow>(`/admin/charging-tariffs/${id}/end`, { validUntil })).data
  },
}

/** Eine Seite, aus der der Tarif-Crawler die Tarife eines Anbieters liest. */
export interface CrawlSource {
  id: string
  empName: string
  url: string
  /** EXTRACT: Preise aus dem Text lesen. WATCH: Preise stehen nur als Bild, Änderungen melden. */
  mode: 'EXTRACT' | 'WATCH'
  enabled: boolean
  lastFetchedAt: string | null
  lastSuccessAt: string | null
  /** OK, UNCHANGED, CHANGED, HTTP_ERROR, ROBOTS, UNREADABLE, EXTRACT_ERROR */
  lastStatus: string | null
  lastHttpStatus: number | null
  lastError: string | null
  consecutiveFailures: number
}

/** Ein Unterschied zwischen Seite und Tabelle, den ein Admin entscheidet. */
export interface CrawlProposal {
  id: string
  sourceId: string
  empName: string
  kind: 'CHANGE' | 'NEW' | 'MISSING' | 'WATCH_CHANGED' | 'MAPPING'
  /** Ein Satz, wie in der Hinweis-Mail. */
  description: string
  proposed: Record<string, unknown>
  /** Gepflegter Tarif, auf den sich der Vorschlag bezieht (CHANGE, MISSING). */
  current: TariffRow | null
  /** Wörtliches Zitat der Seite. */
  evidence: string | null
  holdReason: string | null
  seenCount: number
  firstSeenAt: string
  lastSeenAt: string
}

export interface CrawlOverview {
  sources: CrawlSource[]
  proposals: CrawlProposal[]
  /** false: kein Sprachmodell konfiguriert, nur Bild-Quellen werden geprüft. */
  extractorAvailable: boolean
  enabled: boolean
  /** true: eindeutige Preisänderungen übernimmt der Crawler nach einem zweiten Lauf selbst. */
  autoApply: boolean
}

export interface CrawlSourceCommand {
  empName: string
  url: string
  mode: 'EXTRACT' | 'WATCH'
  enabled: boolean
}

/** Fahrer zahlen mit einer Karte an einem Betreiber deutlich anders als der gepflegte Preis. */
export interface DriverDeviation {
  empName: string
  operator: string
  chargingType: 'AC' | 'DC'
  nearestVariant: string | null
  curatedPricePerKwh: number
  driverMedianPricePerKwh: number
  drivers: number
  charges: number
}

export const adminTariffCrawlService = {
  async driverCheck(): Promise<DriverDeviation[]> {
    return (await api.get<DriverDeviation[]>('/admin/charging-tariffs/crawl/driver-check')).data
  },
  async overview(): Promise<CrawlOverview> {
    return (await api.get<CrawlOverview>('/admin/charging-tariffs/crawl')).data
  },
  /** true: im Hintergrund gestartet, false: ein Lauf ist schon aktiv. */
  async run(): Promise<boolean> {
    const res = await api.post('/admin/charging-tariffs/crawl/run', null, { validateStatus: s => s === 202 || s === 409 })
    return res.status === 202
  },
  /** false: Tarif wurde inzwischen anders gepflegt, der Vorschlag ist überholt. */
  async apply(id: string): Promise<boolean> {
    return (await api.post<{ applied: boolean }>(`/admin/charging-tariffs/crawl/proposals/${id}/apply`)).data.applied
  },
  async reject(id: string): Promise<void> {
    await api.post(`/admin/charging-tariffs/crawl/proposals/${id}/reject`)
  },
  async createSource(cmd: CrawlSourceCommand): Promise<CrawlSource> {
    return (await api.post<CrawlSource>('/admin/charging-tariffs/crawl/sources', cmd)).data
  },
  async updateSource(id: string, cmd: CrawlSourceCommand): Promise<CrawlSource> {
    return (await api.put<CrawlSource>(`/admin/charging-tariffs/crawl/sources/${id}`, cmd)).data
  },
}
