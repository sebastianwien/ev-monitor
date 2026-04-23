import { ref } from 'vue'

type Tab = 'smartcar' | 'spritmonitor' | 'goe' | 'wallbox' | 'tesla' | 'tronity' | 'tessie' | 'manuell' | 'api'

// Module-level singleton — shared across all component instances
const activeTab = ref<Tab | null>(null)

export function useImportsTab() {
  function toggle(tab: Tab) {
    activeTab.value = activeTab.value === tab ? null : tab
  }
  return { activeTab, toggle }
}
