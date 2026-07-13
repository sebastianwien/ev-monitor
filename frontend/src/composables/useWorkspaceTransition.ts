import { WORKSPACE_TABS } from '../config/tabs'

/**
 * Richtung des Uebergangs beim Wechsel zwischen zwei Workspace-Tabs: der neue Inhalt
 * blendet ein und kommt dabei ein Stueck aus der Richtung, in der sein Tab liegt.
 *
 * Bewusst nur ein Anstupser statt eines vollen Slides: Auf Mobile zieht der Finger den
 * Inhalt herein, die Bewegung hat dort eine Herkunft. Ein Klick auf einen Tab hat die
 * nicht - eine Wischbewegung ueber die volle Breite wirkt dann unmotiviert.
 *
 * Wege, die den Streifen verlassen (z.B. /imports), animieren nicht: dort gibt es keine
 * Richtung, die etwas bedeuten wuerde.
 */
const TAB_PATHS: readonly string[] = WORKSPACE_TABS.map(tab => tab.to)

export function slideDirection(from: string, to: string): 'nudge-left' | 'nudge-right' | undefined {
  const fromIndex = TAB_PATHS.indexOf(from)
  const toIndex = TAB_PATHS.indexOf(to)
  if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) return undefined
  return toIndex > fromIndex ? 'nudge-left' : 'nudge-right'
}
