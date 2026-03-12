import { ref } from 'vue'

type Tab = 'spritmonitor' | 'goe' | 'wallbox' | 'tesla'

// Module-level singleton — shared across all component instances
const activeTab = ref<Tab>('spritmonitor')

export function useImportsTab() {
  return { activeTab }
}
