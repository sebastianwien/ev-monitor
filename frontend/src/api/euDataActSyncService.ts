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
  async requestHistory(carId: string): Promise<void> {
    await api.post(`/eu-data-act/cars/${carId}/history`)
  },
  async reactivateSmartcar(carId: string): Promise<void> {
    await api.post(`/eu-data-act/cars/${carId}/reactivate-smartcar`)
  },
}
