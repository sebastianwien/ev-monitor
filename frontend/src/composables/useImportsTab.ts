import { ref } from 'vue'

export type Tab = 'smartcar' | 'spritmonitor' | 'goe' | 'wallbox' | 'tesla' | 'tronity' | 'tessie' | 'xpeng' | 'eu_data_act' | 'manuell' | 'api'

/** Grundreihenfolge der Akkordeon-Sektionen auf /imports - so steht es im Template. */
export const IMPORT_SECTION_ORDER: readonly Tab[] = [
  'tesla', 'smartcar', 'api', 'spritmonitor', 'tronity', 'tessie', 'xpeng', 'eu_data_act', 'manuell', 'goe', 'wallbox',
] as const

/** Signale, aus denen sich die relevanteste Sektion ergibt - alle schon anderswo ermittelt. */
export interface FeaturedSectionContext {
  /** Rueckkehr aus dem Smartcar-OAuth: der User erwartet das Ergebnis seiner Aktion. */
  returningFromSmartcar: boolean
  activeCarIsTesla: boolean
  activeCarIsVwEudaBrand: boolean
  /** XPeng laeuft ueber den eigenen EU-Data-Act-Weg, Smartcar deckt die Marke nicht ab. */
  activeCarIsXpeng: boolean
  hasAutoSync: boolean
}

/**
 * Die fuer den Nutzer relevanteste Sektion. Haengt am **aktiven Auto** (isPrimary, sonst das
 * erste) und damit an einer Auswahl, die der Nutzer selbst trifft - nie am Klickverhalten.
 * Sonst faende er die Liste bei jedem Besuch anders sortiert vor.
 *
 * Steuert beides: welche Sektion aufgeklappt startet und welche oben steht.
 */
export function featuredImportSection(ctx: FeaturedSectionContext): Tab | null {
  if (ctx.returningFromSmartcar) return 'smartcar'
  if (ctx.activeCarIsTesla) return 'tesla'
  if (ctx.activeCarIsVwEudaBrand) return 'eu_data_act'
  if (ctx.activeCarIsXpeng) return 'xpeng'
  if (ctx.hasAutoSync) return 'smartcar'
  return null
}

/** Hervorgehobene Sektion nach oben, alle uebrigen in unveraenderter Reihenfolge dahinter. */
export function importSectionOrder(featured: Tab | null): Tab[] {
  if (!featured) return [...IMPORT_SECTION_ORDER]
  return [featured, ...IMPORT_SECTION_ORDER.filter(section => section !== featured)]
}

// Module-level singleton — shared across all component instances.
const activeTab = ref<Tab | null>(null)

export function useImportsTab() {
  function toggle(tab: Tab) {
    activeTab.value = activeTab.value === tab ? null : tab
  }
  return { activeTab, toggle }
}
