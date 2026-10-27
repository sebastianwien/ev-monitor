/**
 * Chips im Wizard in der Optik von ChoiceChip (App-CI): mindestens 44 px hoch (Daumen-Ziel),
 * Rand und harter Schatten in der Kantenfarbe, gewählt eingedrückt in gray-900.
 * Gestrichelt (offen, warn) ohne Schatten: das sind Einstiege, keine Antworten.
 * Reihen mit Chips stehen rechtsbuendig, damit sie unter dem Daumen liegen.
 */
export const CHIP_ROW = 'flex flex-wrap justify-end gap-2'

const BASE = 'min-h-11 px-4 py-2 rounded-full text-[14px] font-semibold border-2 transition-colors'

export function chipClass(on: boolean, variant: 'solid' | 'dashed' | 'warn' = 'solid'): string {
  if (variant === 'dashed') return `${BASE} border-dashed border-edge text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/10`
  if (variant === 'warn') return `${BASE} border-dashed border-amber-500 text-amber-800 dark:border-amber-400/60 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-400/10`
  return on
    ? `${BASE} btn-3d cta-shadow-sm active border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900`
    : `${BASE} btn-3d cta-shadow-sm border-edge bg-white text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15`
}

/**
 * Antippbare Kacheln (Zusammenfassung, Ortswahl): dieselbe Familie wie die Chips, Kante mit hartem Schatten,
 * beim Drücken und gewählt eingedrückt mit Rand gray-900. `btn-3d` arbeitet mit transform, nicht mit translate:
 * so verträgt es sich mit Einblend-Animationen der Kacheln.
 */
export const CARD = 'btn-3d cta-shadow-sm border-2 rounded-xl text-left transition-colors'
export const CARD_EDGE = 'border-edge'
// Hover nur ab md: auf Touch bliebe er nach dem Tippen stehen
export const CARD_SURFACE = 'bg-white md:hover:bg-gray-50 dark:bg-gray-700/50 md:dark:hover:bg-gray-700/70'
export const CARD_MISSING = 'bg-amber-50 md:hover:bg-amber-100 dark:bg-amber-400/10 md:dark:hover:bg-amber-400/15'
export const CARD_ON = 'active border-gray-900 bg-gray-50 dark:border-gray-100 dark:bg-gray-700/70'

export function cardClass(on: boolean): string {
  return on ? `${CARD} ${CARD_ON}` : `${CARD} ${CARD_EDGE} ${CARD_SURFACE}`
}
