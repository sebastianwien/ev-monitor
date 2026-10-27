/**
 * Segmentgruppen der optionalen Angaben, geteilt von Details-Formular und Pillen-Editor. App-CI: eine Kapsel
 * mit Kante (48 px hoch), Segmente ohne eigene Kante, das gewählte als Fläche gray-900 (dunkel gray-100).
 */
export const SEG_GROUP = 'grid gap-0.5 rounded-full border-2 border-edge bg-white p-0.5 dark:bg-gray-900'
export const SEG_LABEL = 'block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
export const segClass = (on: boolean) => [
  'min-h-10 inline-flex min-w-0 items-center justify-center gap-1.5 rounded-full px-2 text-[14px] font-semibold transition-colors duration-300',
  on ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10',
]
