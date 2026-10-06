import { ref } from 'vue'

/** Zeitpunkt des Einklappens (ms) oder 'false'. Altwert 'true' stammt von vor dem Zeitstempel. */
const STORAGE_KEY = 'ticker-collapsed'
/** Letztes Fetch-Ergebnis: reserviert den Platz schon vor dem Fetch, sonst springt der Inhalt. */
const HAS_ITEMS_KEY = 'ticker-has-items'
/** Nach so langer Zeit klappt ein eingeklappter Ticker einmal wieder auf. */
const REOPEN_AFTER_MS = 7 * 24 * 60 * 60 * 1000

function initialCollapsed(): boolean {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === null || stored === 'false') return false
  const collapsedAt = Number(stored)
  if (stored === 'true' || !Number.isFinite(collapsedAt)) {
    // Altwert ohne Zeitstempel: ab jetzt zählen, damit der Ticker nicht direkt nach dem Deploy aufspringt.
    localStorage.setItem(STORAGE_KEY, String(Date.now()))
    return true
  }
  if (Date.now() - collapsedAt >= REOPEN_AFTER_MS) {
    localStorage.setItem(STORAGE_KEY, 'false')
    return false
  }
  return true
}

export const tickerHasItems = ref(localStorage.getItem(HAS_ITEMS_KEY) === 'true')
export const tickerCollapsed = ref(initialCollapsed())

export function setTickerHasItems(value: boolean) {
  tickerHasItems.value = value
  localStorage.setItem(HAS_ITEMS_KEY, String(value))
}

export function useTickerState() {
  function toggle() {
    tickerCollapsed.value = !tickerCollapsed.value
    localStorage.setItem(STORAGE_KEY, tickerCollapsed.value ? String(Date.now()) : 'false')
  }

  return { tickerHasItems, tickerCollapsed, toggle }
}
