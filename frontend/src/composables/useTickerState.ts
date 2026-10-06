import { ref } from 'vue'

const STORAGE_KEY = 'ticker-collapsed'
/** Letztes Fetch-Ergebnis: reserviert den Platz schon vor dem Fetch, sonst springt der Inhalt. */
const HAS_ITEMS_KEY = 'ticker-has-items'

export const tickerHasItems = ref(localStorage.getItem(HAS_ITEMS_KEY) === 'true')
export const tickerCollapsed = ref(localStorage.getItem(STORAGE_KEY) === 'true')

export function setTickerHasItems(value: boolean) {
  tickerHasItems.value = value
  localStorage.setItem(HAS_ITEMS_KEY, String(value))
}

export function useTickerState() {
  function toggle() {
    tickerCollapsed.value = !tickerCollapsed.value
    localStorage.setItem(STORAGE_KEY, String(tickerCollapsed.value))
  }

  return { tickerHasItems, tickerCollapsed, toggle }
}
