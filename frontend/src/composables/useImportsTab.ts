import { ref } from 'vue'

type Tab = 'smartcar' | 'spritmonitor' | 'goe' | 'wallbox' | 'tesla' | 'tronity' | 'tessie' | 'manuell' | 'api'

// Module-level singleton — shared across all component instances.
// Default to 'smartcar' (AutoSync) expanded since it's the primary import method.
const activeTab = ref<Tab | null>('smartcar')

export function useImportsTab() {
  function toggle(tab: Tab) {
    activeTab.value = activeTab.value === tab ? null : tab
  }
  return { activeTab, toggle }
}
