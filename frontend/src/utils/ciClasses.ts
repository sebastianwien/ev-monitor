/**
 * Klassen der App-CI (docs/design/ci.md) für Knöpfe, Felder und Karten: eine Quelle statt einer Kopie je View.
 * Kante, harter Schatten, 44 px. Sekundär weiß, primär grün, rot nur beim Bestätigen einer Löschung.
 */
export const CI_BTN = 'btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border-2 border-edge px-4 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-50'
export const CI_BTN_SECONDARY = `${CI_BTN} bg-white text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15`
export const CI_BTN_PRIMARY = `${CI_BTN} bg-green-600 text-white hover:bg-green-700`
export const CI_BTN_DANGER = `${CI_BTN} bg-red-600 text-white hover:bg-red-700`
/** Runder Icon-Knopf (Kopieren, Entfernen), 44 px */
export const CI_ICON_BTN = 'btn-3d cta-shadow-sm grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-700 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15'
export const CI_INPUT = 'h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-base text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100'
export const CI_LABEL = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
export const CI_H2 = 'text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100'
/** Karte in einer Box (Liste, Formular, Status) */
export const CI_CARD = 'rounded-2xl bg-white p-4 edge-card dark:bg-gray-700/50'
/** Beta-Kennzeichnung neben einem Titel: Amber, damit sie sich vom grauen Inhalt abhebt */
export const CI_BADGE_BETA = 'rounded-full bg-amber-100 px-2 py-0.5 text-[11.5px] font-bold uppercase tracking-wide text-amber-800 dark:bg-amber-900/40 dark:text-amber-200'
/** Ruhiger Hinweis im Inhalt: grau, ohne Kante, damit er nicht wie etwas zum Drücken aussieht */
export const CI_NOTE = 'rounded-2xl bg-gray-100 p-4 text-sm text-gray-700 dark:bg-gray-800/60 dark:text-gray-200'
