import { formatPeriod } from './formatPeriod'
import type { CapacityOption } from '../api/carService'

export interface OptionLabel {
  /** Hauptzeile: der Zeitraum, sonst der Variantenname bzw. die Kapazitaet. */
  primary: string
  /** Zweite Zeile: die Variante, sofern sie mehr sagt als die Hauptzeile. */
  secondary: string | null
}

/**
 * Beschriftet die Ausfuehrungs-Optionen einer Trim-Gruppe.
 *
 * Der Zeitraum fuehrt, weil das Baujahr das ist, was der Nutzer sicher weiss.
 * Darunter steht die Variante - ohne den Modell-Praefix, der ueber den Buttons
 * ohnehin schon in der Modellauswahl steht ("Model Y LR RWD Juniper" wird zu
 * "LR RWD Juniper"). Ohne diese zweite Zeile muesste man jede Option anklicken,
 * um zu sehen, welche davon das Facelift ist.
 */
export function buildOptionLabels(
  options: CapacityOption[],
  modelLabel = '',
  sinceLabel = 'seit',
): OptionLabel[] {
  return options.map(o => {
    const period = formatPeriod(o.availableFrom, o.availableTo, sinceLabel)
    const variant = stripModelPrefix(o.variantName, modelLabel)
    if (period) return { primary: period, secondary: variant }
    return { primary: variant ?? `${o.kWh} kWh`, secondary: null }
  })
}

function stripModelPrefix(variantName: string | null, modelLabel: string): string | null {
  if (!variantName) return null
  const prefix = `${modelLabel} `
  const stripped = modelLabel && variantName.startsWith(prefix)
    ? variantName.slice(prefix.length)
    : variantName
  return stripped.trim() || null
}
