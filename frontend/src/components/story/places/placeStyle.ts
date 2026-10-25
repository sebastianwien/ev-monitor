import type { PlaceClass } from '../../../composables/storyPlaces'

/**
 * Füllung je Preisklasse, gemeinsam für Punkt auf der Karte, Punkt in der Liste und Legende.
 * Üblich ist blau wie "öffentlich" im Strom-Balken (FlowBar), grau wirkte unwichtig.
 * Amber nur für teurer, nie rot. Zuhause trägt sein Haus-Symbol auf Grün.
 */
export const PLACE_FILL: Record<PlaceClass, string> = {
  home: 'bg-green-700 text-white',
  low: 'bg-green-600',
  usual: 'bg-sky-500 dark:bg-sky-400',
  none: 'bg-white dark:bg-gray-700',
  high: 'bg-amber-500',
}

/** Kante der Punkte wie btn-3d: 2 px gray-900, harter Schatten; dunkel ohne Schatten. */
export const PIN_EDGE = 'rounded-full border-2 border-gray-900 dark:border-gray-50'
