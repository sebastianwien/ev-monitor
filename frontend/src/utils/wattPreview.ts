/**
 * "+n Watt"-Vorschau VOR einer Aktion. Rechnet aus dem Belohnungskatalog des Backends
 * (GET /coins/catalog) und dem Formularzustand, was das Speichern bringen wuerde.
 * Die Betraege leben nur im Backend - hier wird nichts hart codiert.
 */
export interface WattCatalogEntry { amount: number; oneTime: boolean; claimed: boolean }
export type WattCatalog = Record<string, WattCatalogEntry>

/** Spiegel von EvLogService.BATCH_REWARD_CAP: mehr bepreiste Ladungen pro Batch zaehlen nicht. */
export const BATCH_REWARD_CAP = 20

export interface WattPreviewState {
  /** Ladung hatte keinen Preis und bekommt jetzt einen. */
  addsPrice: boolean
  /** Ladung hatte keine Karte und bekommt jetzt eine. */
  addsCard: boolean
  /** Ladung hatte keinen CPO und bekommt jetzt einen. */
  addsCpo: boolean
  /** Weitere Ladungen, die der Sammel-Nachtrag bepreist (0 = kein Batch). */
  batchCount: number
  /** Es wird eine neue Ladekarte angelegt (einmaliger Bonus). */
  createsCard?: boolean
}

function amountOf(catalog: WattCatalog, event: string): number {
  const e = catalog[event]
  if (!e) return 0
  return e.oneTime && e.claimed ? 0 : e.amount
}

export function wattPreview(catalog: WattCatalog | null, s: WattPreviewState): number {
  if (!catalog) return 0
  let total = 0
  if (s.addsPrice) total += amountOf(catalog, 'PRICE_ADDED')
  if (s.addsCard) total += amountOf(catalog, 'CARD_LINKED')
  if (s.addsCpo) total += amountOf(catalog, 'CPO_ADDED')
  if (s.batchCount > 0) total += Math.min(s.batchCount, BATCH_REWARD_CAP) * amountOf(catalog, 'PRICE_ADDED')
  if (s.createsCard) total += amountOf(catalog, 'CARD_CREATED')
  return total
}

/** Was eine einzelne Ladung beim Nachtragen noch bringen KANN (Obergrenze fuer Chips und Listen). */
export function wattPossibleForLog(catalog: WattCatalog | null, log: {
  costEur?: number | null; chargingProviderId?: string | null; cpoName?: string | null; isPublicCharging?: boolean | null
}): number {
  return wattPreview(catalog, {
    addsPrice: log.costEur == null,
    addsCard: log.chargingProviderId == null,
    addsCpo: !!log.isPublicCharging && !log.cpoName,
    batchCount: 0,
  })
}

/** Obergrenze pro Ladung ohne Kenntnis der Ladung (Banner mit N Ladungen). */
export function wattPossiblePerLogMax(catalog: WattCatalog | null): number {
  return wattPreview(catalog, { addsPrice: true, addsCard: true, addsCpo: true, batchCount: 0 })
}
