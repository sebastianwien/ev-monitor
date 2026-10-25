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
  /** validUntil: letzter Tag, an dem der Tarif noch gilt (ISO-Datum). */
  async end(id: string, validUntil: string): Promise<TariffRow> {
    return (await api.post<TariffRow>(`/admin/charging-tariffs/${id}/end`, { validUntil })).data
  },
}
