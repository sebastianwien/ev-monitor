/**
 * Auswertung des Investitionsfelds.
 *
 * v-model auf einem <input type="number"> liefert eine Zahl, sobald der Wert gueltig ist,
 * und den Rohstring, solange er es nicht ist (leer, "1e", "-"). Die Pruefung muss deshalb
 * mit beidem umgehen - deshalb liegt sie hier als eigene Funktion statt in der Komponente.
 */

/** Spiegelt die serverseitige Obergrenze, damit die Ablehnung nicht erst beim Absenden kommt. */
export const MAX_INVESTMENT = 100000

export interface ParsedInvestment {
  valid: boolean
  /** null loescht den hinterlegten Wert. */
  value: number | null
}

export function parseInvestmentInput(raw: unknown): ParsedInvestment {
  if (raw == null) return { valid: true, value: null }

  const text = String(raw).trim()
  if (text === '') return { valid: true, value: null }

  const value = Number(text)
  if (!Number.isFinite(value) || value < 0 || value > MAX_INVESTMENT) {
    return { valid: false, value: null }
  }
  return { valid: true, value }
}
