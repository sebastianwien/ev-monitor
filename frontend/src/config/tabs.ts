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

/** Wisch-Paar des CarContextLayout: im neuen Design (Story-Opt-in) steht die Übersicht an der Stelle des Dashboards. */
export function contextTabPaths(storyDesign: boolean): readonly string[] {
  return storyDesign ? ['/story', CONTEXT_TABS[1].to] : CONTEXT_TABS.map(tab => tab.to)
}

/** barLabelKey: kurze Beschriftung in der Navbar (wenn sie dort passt), der Seitentitel bleibt "Meine ...". */
export const CAR_TABS = [
  { to: '/cars', labelKey: 'cars.title', barLabelKey: 'nav.vehicles' },
  { to: '/charging-providers', labelKey: 'settings.tariff_title', barLabelKey: 'nav.cards' },
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

/**
 * Weitere Ziele des eingeloggten Bereichs, nicht Teil der Wisch-Tabs: im neuen Design im Kontomenü
 * der Navbar (ab md), mobil im Mehr-Menü.
 */
export const WORKSPACE_LINKS = [
  { to: '/imports', labelKey: 'nav.bottom.import' },
  { to: '/modelle', labelKey: 'nav.models_compare' },
] as const
