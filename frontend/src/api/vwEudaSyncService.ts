import api from './axios'

/** Verbindung eines Fahrzeugs zum VW EU-Data-Act-Portal (AutoSync), wie der Connectors-Service sie meldet. */
export interface VwEudaConnectionStatus {
  carId: string
  brand: string
  email: string
  vin: string | null
  /** EXPIRED: Trial vorbei, kein Abo - Abgleich pausiert, Kauf setzt ihn fort. */
  status: 'ACTIVE' | 'AUTH_FAILED' | 'EXPIRED'
  lastSuccessAt: string | null
  historyImportedAt: string | null
  lastError: string | null
}

/** Herstellerneutrales Sync-Protokoll: Datenanfragen, Lieferungen, Importe - auch fuer XPeng/Polestar nutzbar. */
export type VwEudaDeliveryOutcome = 'IMPORTED' | 'NO_CHARGING_DATA' | 'FAILED'
export type VwEudaPollOutcome = 'OK' | 'NO_NEW_DATA' | 'PORTAL_ERROR' | 'IMPORT_ERROR' | 'AUTH_FAILED'

export interface VwEudaIdentifier {
  label: string
  value: string
}

export interface VwEudaHistoryState {
  requestedAt: string | null
  importedAt: string | null
  running: boolean
  attempts: number
  attemptsExhausted: boolean
  error: string | null
}

export interface VwEudaActivityConnection {
  carId: string
  brand: string
  status: 'ACTIVE' | 'AUTH_FAILED' | 'EXPIRED'
  connectedAt: string
  lastPolledAt: string | null
  lastSuccessAt: string | null
  consecutiveFailures: number
  lastError: string | null
  /** Beim Hersteller liegt eine laufende Datenanfrage vor. */
  dataRequestActive: boolean
  lastDeliveryAt: string | null
  lastDataAt: string | null
  history: VwEudaHistoryState | null
}

export interface VwEudaActivitySummary {
  deliveriesSeen: number
  deliveriesWithContent: number
  sessionsImported: number
  lastContentAt: string | null
}

export interface VwEudaPollEntry {
  at: string
  outcome: VwEudaPollOutcome
  deliveriesSeen: number
  deliveriesWithContent: number
  sessionsImported: number
  sessionsSkipped: number
  history: boolean
  error: string | null
}

export interface VwEudaDeliveryEntry {
  filename: string
  createdOn: string
  sizeBytes: number
  outcome: VwEudaDeliveryOutcome | null
  sessionsImported: number | null
  sessionsSkipped: number | null
  error: string | null
}

export interface VwEudaSyncActivity {
  provider: string
  manufacturerContact: string
  connection: VwEudaActivityConnection
  identifiers: VwEudaIdentifier[]
  summary: VwEudaActivitySummary
  polls: VwEudaPollEntry[]
  deliveries: VwEudaDeliveryEntry[]
}

export type VwEudaBrand = 'volkswagen' | 'skoda' | 'audi' | 'seat' | 'cupra'

/** CarBrand-Enum (Backend) -> Portal-Marke. Nur diese Marken bedient das VW-EU-Data-Act-Portal. */
const EUDA_BRAND_BY_CAR_BRAND: Record<string, VwEudaBrand> = {
  VW: 'volkswagen', VOLKSWAGEN: 'volkswagen', SKODA: 'skoda', AUDI: 'audi', SEAT: 'seat', CUPRA: 'cupra',
}

export function eudaBrandOf(carBrand: string): VwEudaBrand | null {
  return EUDA_BRAND_BY_CAR_BRAND[carBrand?.toUpperCase() ?? ""] ?? null
}

export function isVwEudaBrand(carBrand: string): boolean {
  return eudaBrandOf(carBrand) !== null
}

/** Fehlercodes des Connectors - das Frontend mappt sie auf i18n-Texte. */
export type VwEudaErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'NOT_ENTITLED'
  | 'PORTAL_INTERACTION_REQUIRED'
  | 'PORTAL_UNAVAILABLE'
  | 'CAPACITY_REACHED'
  | 'RATE_LIMITED'
  | 'FORBIDDEN'
  | 'BAD_REQUEST'

export function eudaErrorCode(err: unknown): VwEudaErrorCode | null {
  const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
  return (code as VwEudaErrorCode) ?? null
}

/** Entscheidung des Core: Abo, Rolle oder launch-verankertes Trial. */
export interface VwEudaEntitlement {
  entitled: boolean
  viaTrial: boolean
  trialEndsAt: string | null
}

export default {
  /** Liegt beim Core, nicht beim Connector - deshalb unter /subscription. */
  async getEntitlement(): Promise<VwEudaEntitlement> {
    const resp = await api.get('/subscription/eu-data-act-autosync')
    return resp.data
  },
  async getStatus(): Promise<VwEudaConnectionStatus[]> {
    const resp = await api.get('/eu-data-act/status')
    return resp.data
  },
  /** Das Passwort wird nur für diesen Aufruf übertragen; der Server speichert es nicht. */
  async connect(carId: string, brand: VwEudaBrand, email: string, password: string): Promise<VwEudaConnectionStatus> {
    const resp = await api.post(`/eu-data-act/cars/${carId}/connect`, { brand, email, password })
    return resp.data
  },
  async disconnect(carId: string): Promise<void> {
    await api.delete(`/eu-data-act/cars/${carId}`)
  },
  async getActivity(carId: string): Promise<VwEudaSyncActivity> {
    const resp = await api.get(`/eu-data-act/cars/${carId}/activity`)
    return resp.data
  },
  async requestHistory(carId: string): Promise<void> {
    await api.post(`/eu-data-act/cars/${carId}/history`)
  },
  async reactivateSmartcar(carId: string): Promise<void> {
    await api.post(`/eu-data-act/cars/${carId}/reactivate-smartcar`)
  },
}
