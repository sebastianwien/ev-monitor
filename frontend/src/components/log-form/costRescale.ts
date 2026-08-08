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
