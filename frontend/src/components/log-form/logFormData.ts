/** Verweis auf einen Ladestandort: Name plus Geohash-Zelle der Säule. */
export interface ChargingSiteRef {
  name: string
  geohash: string
}

/** Formularzustand eines Ladevorgangs - geteilt von Wizard, Editor und klassischem Formular. */
export interface LogFormData {
  kwhCharged: number | null
  costEur: number | null
  costExchangeRate: number | null
  costCurrency: string | null
  odometerKm: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
  kwhAtVehicle: number | null
  chargeDurationMinutes: number | null
  maxChargingPowerKw: number | null
  loggedAt: string | null
  chargingType: 'AC' | 'DC'
  routeType: 'CITY' | 'COMBINED' | 'HIGHWAY'
  tireType: 'SUMMER' | 'ALL_YEAR' | 'WINTER'
  latitude: number | null
  longitude: number | null
  isPublicCharging: boolean
  cpoName: string | null
  chargingProviderId: string | null
  /** Register-Säule, an der geladen wurde - der Server prüft sie gegen das Register und legt den Standort an. */
  chargingSite: ChargingSiteRef | null
  /**
   * Geohash of an already-stored log (edit mode). lat/lon are never persisted, so when editing
   * an imported charge this is the only location the client has.
   */
  geohash?: string | null
  /** Opt-in: after saving, price all cost-less logs at this location with the selected card. */
  applyTariffToLocation?: boolean
}
