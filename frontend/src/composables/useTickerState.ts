import { computed, ref } from 'vue'

/** Zeitpunkt des Einklappens (ms) oder 'false'. Altwert 'true' stammt von vor dem Zeitstempel. */
const STORAGE_KEY = 'ticker-collapsed'
/** Letztes Fetch-Ergebnis: reserviert den Platz schon vor dem Fetch, sonst springt der Inhalt. */
const HAS_ITEMS_KEY = 'ticker-has-items'
const HIDDEN_KEY = 'ticker-hidden'
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

const hasItems = ref(localStorage.getItem(HAS_ITEMS_KEY) === 'true')
/** In den Einstellungen ausgeblendet (je Gerät). Erfüllt WCAG 2.2.2, wo das Band keine Lasche und keinen Pause-Knopf hat. */
export const tickerHidden = ref(localStorage.getItem(HIDDEN_KEY) === 'true')
/** Ticker belegt Platz: Einträge vorhanden und nicht ausgeblendet. */
export const tickerHasItems = computed(() => hasItems.value && !tickerHidden.value)
export const tickerCollapsed = ref(initialCollapsed())

export function setTickerHasItems(value: boolean) {
  hasItems.value = value
  localStorage.setItem(HAS_ITEMS_KEY, String(value))
}

export function setTickerHidden(value: boolean) {
  tickerHidden.value = value
  localStorage.setItem(HIDDEN_KEY, String(value))
}

export function useTickerState() {
  function toggle() {
    tickerCollapsed.value = !tickerCollapsed.value
    localStorage.setItem(STORAGE_KEY, tickerCollapsed.value ? String(Date.now()) : 'false')
  }

  return { tickerHasItems, tickerCollapsed, toggle }
}
