import api from './axios'

/** Verbindung eines Fahrzeugs zum VW EU-Data-Act-Portal (AutoSync), wie der Connectors-Service sie meldet. */
export interface EudaConnectionStatus {
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
export type EudaDeliveryOutcome = 'IMPORTED' | 'NO_CHARGING_DATA' | 'FAILED'
export type EudaPollOutcome = 'OK' | 'NO_NEW_DATA' | 'PORTAL_ERROR' | 'IMPORT_ERROR' | 'AUTH_FAILED'

export interface EudaIdentifier {
  label: string
  value: string
}

export interface EudaHistoryState {
  requestedAt: string | null
  importedAt: string | null
  running: boolean
  attempts: number
  attemptsExhausted: boolean
  error: string | null
}

export interface EudaActivityConnection {
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
  history: EudaHistoryState | null
}

export interface EudaActivitySummary {
  deliveriesSeen: number
  deliveriesWithContent: number
  sessionsImported: number
  lastContentAt: string | null
}

export interface EudaPollEntry {
  at: string
  outcome: EudaPollOutcome
  deliveriesSeen: number
  deliveriesWithContent: number
  sessionsImported: number
  sessionsSkipped: number
  history: boolean
  error: string | null
}

export interface EudaDeliveryEntry {
  filename: string
  createdOn: string
  sizeBytes: number
  outcome: EudaDeliveryOutcome | null
  sessionsImported: number | null
  sessionsSkipped: number | null
  error: string | null
}

export interface EudaSyncActivity {
  provider: string
  manufacturerContact: string
  connection: EudaActivityConnection
  identifiers: EudaIdentifier[]
  summary: EudaActivitySummary
  polls: EudaPollEntry[]
  deliveries: EudaDeliveryEntry[]
}

export type EudaBrand = 'volkswagen' | 'skoda' | 'audi' | 'seat' | 'cupra'

/** CarBrand-Enum (Backend) -> Portal-Marke. Nur diese Marken bedient das VW-EU-Data-Act-Portal. */
const EUDA_BRAND_BY_CAR_BRAND: Record<string, EudaBrand> = {
  VW: 'volkswagen', VOLKSWAGEN: 'volkswagen', SKODA: 'skoda', AUDI: 'audi', SEAT: 'seat', CUPRA: 'cupra',
}

export function eudaBrandOf(carBrand: string): EudaBrand | null {
  return EUDA_BRAND_BY_CAR_BRAND[carBrand?.toUpperCase() ?? ""] ?? null
}

export function isEudaBrand(carBrand: string): boolean {
  return eudaBrandOf(carBrand) !== null
}

/** Fehlercodes des Connectors - das Frontend mappt sie auf i18n-Texte. */
export type EudaErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'NOT_ENTITLED'
  | 'PORTAL_INTERACTION_REQUIRED'
  | 'PORTAL_UNAVAILABLE'
  | 'CAPACITY_REACHED'
  | 'RATE_LIMITED'
  | 'FORBIDDEN'
  | 'BAD_REQUEST'

export function eudaErrorCode(err: unknown): EudaErrorCode | null {
  const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
  return (code as EudaErrorCode) ?? null
}

/** Entscheidung des Core: Abo, Rolle oder launch-verankertes Trial. */
export interface EudaEntitlement {
  entitled: boolean
  viaTrial: boolean
  trialEndsAt: string | null
}

export default {
  /** Liegt beim Core, nicht beim Connector - deshalb unter /subscription. */
  async getEntitlement(): Promise<EudaEntitlement> {
    const resp = await api.get('/subscription/eu-data-act-autosync')
    return resp.data
  },
  async getStatus(): Promise<EudaConnectionStatus[]> {
    const resp = await api.get('/eu-data-act/status')
    return resp.data
  },
  /** Das Passwort wird nur für diesen Aufruf übertragen; der Server speichert es nicht. */
  async connect(carId: string, brand: EudaBrand, email: string, password: string): Promise<EudaConnectionStatus> {
    const resp = await api.post(`/eu-data-act/cars/${carId}/connect`, { brand, email, password })
    return resp.data
  },
  async disconnect(carId: string): Promise<void> {
    await api.delete(`/eu-data-act/cars/${carId}`)
  },
  async getActivity(carId: string): Promise<EudaSyncActivity> {
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
