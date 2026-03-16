import { ref } from 'vue'

type Tab = 'spritmonitor' | 'goe' | 'wallbox' | 'tesla' | 'manuell' | 'api'

// Module-level singleton — shared across all component instances
const activeTab = ref<Tab>('spritmonitor')

export function useImportsTab() {
  return { activeTab }
}
