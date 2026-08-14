import { costBasisKwh } from '../composables/useChargingEfficiency'

/**
 * Passt den Gesamtbetrag an, wenn sich die Energiemenge einer Ladung aendert.
 *
 * Feste Groesse ist der ct/kWh-Preis, nicht der Betrag: ein Ladepunkt rechnet brutto ab, also
 * gehoert zu mehr kWh auch mehr Geld. Wer nach dem Speichern die Bruttomenge ergaenzt, sah sonst
 * denselben Betrag auf mehr kWh verteilt - der ct/kWh-Preis sank, obwohl der Tarif galt.
 *
 * Ohne verwertbare alte Menge gibt es keinen ableitbaren ct/kWh-Preis; dann bleibt der Betrag
 * unangetastet, statt zu raten.
 */
export function rescaleTotalToNewEnergy(
    total: number | null,
    previousKwh: number | null,
    newKwh: number | null,
): number | null {
  if (total == null) return null
  if (!previousKwh || !newKwh) return total
  return Math.round((total / previousKwh) * newKwh * 100) / 100
}

export interface CostRescaleLog {
  costEur: number | null
  kwhCharged: number | null
  kwhAtVehicle: number | null
}

/**
 * Neuer Gesamtbetrag, wenn eine kWh-Menge eines gespeicherten Logs gesetzt oder geaendert wird.
 *
 * Massgeblich ist die Bezugsmenge, auf der der Betrag abgerechnet wurde ({@link costBasisKwh}:
 * brutto vor netto). Wechselt sie - typisch beim Nachtragen der Bruttomenge - muss der Betrag
 * mitwachsen, sonst faellt der angezeigte ct/kWh-Preis um die Ladeverluste, obwohl der Tarif gilt.
 * Bleibt die Bezugsmenge gleich (z.B. Netto zu einem Brutto-Log), bleibt der Betrag unangetastet.
 */
export function rescaleCostForKwhChange(
    log: CostRescaleLog,
    field: 'kwhCharged' | 'kwhAtVehicle',
    newValue: number,
): number | null {
  return rescaleTotalToNewEnergy(
      log.costEur,
      costBasisKwh(log),
      costBasisKwh({ ...log, [field]: newValue }),
  )
}
