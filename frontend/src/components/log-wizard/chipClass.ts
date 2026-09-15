/**
 * Chips im Wizard: mindestens 44 px hoch (Daumen-Ziel), gemeinsame Optik.
 * Reihen mit Chips stehen rechtsbuendig, damit sie unter dem Daumen liegen.
 */
export const CHIP_ROW = 'flex flex-wrap justify-end gap-2'

const BASE = 'min-h-11 px-4 py-2 rounded-full text-sm border transition'

export function chipClass(on: boolean, variant: 'solid' | 'dashed' | 'warn' = 'solid'): string {
  if (variant === 'dashed') return `${BASE} border-dashed border-gray-400 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700`
  if (variant === 'warn') return `${BASE} border-dashed border-amber-400 text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/20`
  return on
    ? `${BASE} bg-indigo-600 text-white border-indigo-600 hover:bg-indigo-700`
    : `${BASE} border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 hover:border-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30`
}
