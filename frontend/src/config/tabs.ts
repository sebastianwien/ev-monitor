/**
 * Die Tabs des eingeloggten Bereichs an einer Stelle, damit Desktop-Leiste und
 * Mobile-Pager nie auseinanderlaufen.
 *
 * Mobile wischt man paarweise: Dashboard <-> Log-Feed (CarContextLayout) und
 * Fahrzeuge <-> Ladekarten (CarsLayout). Desktop zeigt alle vier Ziele nebeneinander,
 * ein Klick pro Ziel.
 */
export const CONTEXT_TABS = [
  { to: '/dashboard', labelKey: 'nav.dashboard' },
  { to: '/logs', labelKey: 'logs.title' },
] as const

export const CAR_TABS = [
  { to: '/cars', labelKey: 'cars.title' },
  { to: '/charging-providers', labelKey: 'settings.tariff_title' },
] as const

/**
 * Reihenfolge der Desktop-Leiste: vom Taeglichen (Dashboard, Logs) zum Seltenen
 * (Ladekarten, Fahrzeuge). Die Ladekarten stehen bewusst vor den Fahrzeugen - sie
 * sind der haeufigere Grund, die Verwaltung ueberhaupt zu oeffnen.
 */
export const WORKSPACE_TABS = [
  CONTEXT_TABS[0],
  CONTEXT_TABS[1],
  CAR_TABS[1],
  CAR_TABS[0],
] as const
