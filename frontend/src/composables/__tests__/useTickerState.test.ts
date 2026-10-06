import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('useTickerState', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  it('reserviert den Platz beim Start, wenn der Ticker zuletzt Einträge hatte (kein Layout-Sprung)', async () => {
    localStorage.setItem('ticker-has-items', 'true')
    const { tickerHasItems } = await import('../useTickerState')
    expect(tickerHasItems.value).toBe(true)
  })

  it('merkt sich das Fetch-Ergebnis für den nächsten Start', async () => {
    const { setTickerHasItems } = await import('../useTickerState')
    setTickerHasItems(true)
    expect(localStorage.getItem('ticker-has-items')).toBe('true')
    setTickerHasItems(false)
    expect(localStorage.getItem('ticker-has-items')).toBe('false')
  })
})

describe('useTickerState - Wieder-Aufklappen nach 7 Tagen', () => {
  const DAY = 24 * 60 * 60 * 1000
  const NOW = new Date('2026-10-06T10:00:00Z').getTime()

  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('speichert beim Einklappen den Zeitpunkt', async () => {
    const { useTickerState } = await import('../useTickerState')
    const { tickerCollapsed, toggle } = useTickerState()

    toggle()

    expect(tickerCollapsed.value).toBe(true)
    expect(localStorage.getItem('ticker-collapsed')).toBe(String(NOW))
  })

  it('bleibt vor Ablauf von 7 Tagen eingeklappt', async () => {
    localStorage.setItem('ticker-collapsed', String(NOW - 6 * DAY))
    const { tickerCollapsed } = await import('../useTickerState')
    expect(tickerCollapsed.value).toBe(true)
  })

  it('klappt nach 7 Tagen einmal auf', async () => {
    localStorage.setItem('ticker-collapsed', String(NOW - 7 * DAY))
    const { tickerCollapsed } = await import('../useTickerState')

    expect(tickerCollapsed.value).toBe(false)
    expect(localStorage.getItem('ticker-collapsed')).toBe('false')
  })

  it('zählt den Altwert "true" ab dem ersten Laden, ohne sofort aufzuspringen', async () => {
    localStorage.setItem('ticker-collapsed', 'true')
    const { tickerCollapsed } = await import('../useTickerState')

    expect(tickerCollapsed.value).toBe(true)
    expect(localStorage.getItem('ticker-collapsed')).toBe(String(NOW))
  })

  it('Aufklappen von Hand löscht den Zeitpunkt', async () => {
    localStorage.setItem('ticker-collapsed', String(NOW - DAY))
    const { useTickerState } = await import('../useTickerState')
    const { tickerCollapsed, toggle } = useTickerState()

    toggle()

    expect(tickerCollapsed.value).toBe(false)
    expect(localStorage.getItem('ticker-collapsed')).toBe('false')
  })
})
