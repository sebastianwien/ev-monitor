import { describe, it, expect, beforeEach, vi } from 'vitest'

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
