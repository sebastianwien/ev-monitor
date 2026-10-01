/** Segmentgruppen der optionalen Angaben: gleich breite Zellen, 44 px hoch, geteilt von Details-Formular und Pillen-Editor. */
export const SEG_GROUP = 'grid gap-px overflow-hidden rounded-sm border border-gray-300 dark:border-gray-600 bg-gray-300 dark:bg-gray-600'
export const SEG_LABEL = 'block text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500'
export const segClass = (on: boolean) => [
  'min-h-11 inline-flex items-center justify-center gap-1.5 px-2 text-sm transition',
  on ? 'bg-indigo-600 text-white' : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/30',
]
