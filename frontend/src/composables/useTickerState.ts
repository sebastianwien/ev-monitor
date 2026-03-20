import { ref } from 'vue'

const STORAGE_KEY = 'ticker-collapsed'

export const tickerHasItems = ref(false)
export const tickerCollapsed = ref(localStorage.getItem(STORAGE_KEY) === 'true')

export function useTickerState() {
  function toggle() {
    tickerCollapsed.value = !tickerCollapsed.value
    localStorage.setItem(STORAGE_KEY, String(tickerCollapsed.value))
  }

  return { tickerHasItems, tickerCollapsed, toggle }
}
