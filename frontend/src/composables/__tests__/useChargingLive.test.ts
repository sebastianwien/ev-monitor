import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn() },
}))
import api from '../../api/axios'

const activeData = {
  isActive: true, chargingType: 'DC', powerKw: 50, socPercent: 40,
  energyRemainingKwh: null, timeToFullMinutes: null, estRangeKm: null,
  chargeAmps: null,
  sessionStartedAt: '2026-06-04T16:38:00Z', sessionEndedAt: null,
  socAtSessionStart: 10, chargeLimitSoc: 80,
  lastUpdatedAt: '2026-06-04T17:00:00Z',
}

// Full session curve as the server reconstructs it from vehicle_signal_events:
// ramp -> plateau -> taper. The FIRST point is the session start.
const historyResponse = {
  points: [
    { timestamp: '2026-06-04T16:38:00Z', powerKw: 45 },
    { timestamp: '2026-06-04T17:00:00Z', powerKw: 49 },
    { timestamp: '2026-06-04T17:30:00Z', powerKw: 36 },
  ],
}

// Captures setTimeout calls without faking timers (avoids async loop issues)
let capturedTimers: Array<{ callback: () => unknown; delay: number; id: number }> = []
let timerIdCounter = 0

function isHistoryUrl(url: unknown): boolean {
  return String(url).includes('/charging/history')
}
function historyCallCount(): number {
  return vi.mocked(api.get).mock.calls.filter(c => isHistoryUrl(c[0])).length
}
function mockApiActive() {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (isHistoryUrl(url)) return Promise.resolve({ data: historyResponse }) as never
    if (String(url).includes('/live/charging')) return Promise.resolve({ data: activeData }) as never
    return Promise.reject(new Error('unexpected url ' + url)) as never
  })
}
async function flush() {
  for (let i = 0; i < 15; i++) await Promise.resolve()
}

beforeEach(() => {
  capturedTimers = []
  timerIdCounter = 0
  vi.clearAllMocks()
  vi.stubGlobal('setTimeout', (callback: () => unknown, delay: number) => {
    const id = ++timerIdCounter
    capturedTimers.push({ callback, delay, id })
    return id
  })
  vi.stubGlobal('clearTimeout', () => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useChargingLive power history', () => {
  it('fetches the full session history on the first active poll', async () => {
    mockApiActive()
    const { useChargingLive } = await import('../useChargingLive')
    const { powerHistory } = useChargingLive(ref('car-1'))
    await flush()
    expect(historyCallCount()).toBeGreaterThanOrEqual(1)
    expect(powerHistory.value.map(p => p.kw)).toEqual([45, 49, 36])
  })

  it('re-fetches the full history on every active poll so the session start never scrolls out', async () => {
    mockApiActive()
    const { useChargingLive } = await import('../useChargingLive')
    const { powerHistory } = useChargingLive(ref('car-1'))
    await flush()
    const afterFirst = historyCallCount()

    // Fire the scheduled timer -> simulates the next 10s poll tick.
    const timer = capturedTimers[capturedTimers.length - 1]
    await timer.callback()
    await flush()

    // The fix: history is re-pulled, not guarded to once-per-session.
    expect(historyCallCount()).toBeGreaterThan(afterFirst)
    // And powerHistory is the server curve verbatim - no client-appended live
    // point that would later push the session start out of the 120-point window.
    expect(powerHistory.value.map(p => p.kw)).toEqual([45, 49, 36])
  })
})
