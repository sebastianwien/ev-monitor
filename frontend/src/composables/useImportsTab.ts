import { ref } from 'vue'

type Tab = 'smartcar' | 'spritmonitor' | 'goe' | 'wallbox' | 'tesla' | 'tronity' | 'manuell' | 'api'

// Module-level singleton — shared across all component instances
const activeTab = ref<Tab>('smartcar')

export function useImportsTab() {
  return { activeTab }
}
