import type { UseKey } from '../../composables/storyEnergy'

/** Farben für Fahren, Stand und Laden: Fahren grau wie kurze Fahrten, Verluste in Amber, kein Grün (kein Ergebnis, keine Handlung). */
export const USE_STYLE: Record<UseKey, { label: string; cls: string; text: string }> = {
  drive: { label: 'story.use_drive', cls: 'bg-gray-700 dark:bg-gray-300', text: 'text-gray-700 dark:text-gray-300' },
  standby: { label: 'story.use_standby', cls: 'bg-amber-500', text: 'text-amber-700 dark:text-amber-400' },
  charge: { label: 'story.use_charge', cls: 'bg-amber-300', text: 'text-amber-700 dark:text-amber-300' },
}

/** Schraffur für eine gesperrte Fläche. */
export const LOCKED_CLS = 'bg-[repeating-linear-gradient(135deg,var(--color-gray-300)_0_3px,var(--color-gray-200)_3px_6px)] dark:bg-[repeating-linear-gradient(135deg,var(--color-gray-600)_0_3px,var(--color-gray-700)_3px_6px)]'
