import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import smartcarService from '../../api/smartcarService'

vi.mock('../../api/smartcarService')

const connectedCharging = {
  connected: true, vehicleName: 'BMW i3', carId: 'car-1', vin: 'VIN1',
  vehicleState: 'CHARGING', lastCheckedAt: '2026-01-01T10:00:00',
  lastSoc: 55, sessionActive: true, sessionStartedAt: '2026-01-01T09:00:00', sessionEnergyAdded: 12.5
}
const connectedNotCharging = {
  ...connectedCharging, vehicleState: 'NOT_CHARGING',
  sessionActive: false, sessionStartedAt: null, sessionEnergyAdded: null
}
const notConnected = {
  connected: false, vehicleName: null, carId: null, vin: null,
  vehicleState: null, lastCheckedAt: null, lastSoc: null,
  sessionActive: false, sessionStartedAt: null, sessionEnergyAdded: null
}

// Captures setTimeout calls without faking timers (avoids async loop issues)
let capturedTimers: Array<{ callback: () => unknown; delay: number; id: number }> = []
let capturedClearTimeouts: number[] = []
let timerIdCounter = 0

beforeEach(() => {
  capturedTimers = []
  capturedClearTimeouts = []
  timerIdCounter = 0
  vi.clearAllMocks()
  vi.stubGlobal('setTimeout', (callback: () => unknown, delay: number) => {
    const id = ++timerIdCounter
    capturedTimers.push({ callback, delay, id })
    return id
  })
  vi.stubGlobal('clearTimeout', (id: number) => {
    capturedClearTimeouts.push(id)
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useSmartcarStatus', () => {
  it('start(false) does not fetch status', async () => {
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { smartcarStatus, start } = useSmartcarStatus()
    await start(false)
    expect(smartcarService.getStatus).not.toHaveBeenCalled()
    expect(smartcarStatus.value).toBeNull()
  })

  it('start(true) fetches status immediately and then schedules poll', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(connectedCharging)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { smartcarStatus, start } = useSmartcarStatus()
    await start(true)
    expect(smartcarService.getStatus).toHaveBeenCalledOnce()
    expect(smartcarStatus.value).toEqual(connectedCharging)
    expect(capturedTimers.length).toBe(1)
  })

  it('schedules 2min poll when charging', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(connectedCharging)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { start } = useSmartcarStatus()
    await start(true)
    expect(capturedTimers[0].delay).toBe(2 * 60 * 1000)
  })

  it('schedules 5min poll when not charging', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(connectedNotCharging)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { start } = useSmartcarStatus()
    await start(true)
    expect(capturedTimers[0].delay).toBe(5 * 60 * 1000)
  })

  it('re-polls when the scheduled timer fires', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(connectedCharging)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { start } = useSmartcarStatus()
    await start(true)
    vi.clearAllMocks()

    // Manually fire the scheduled timer (simulates time passing)
    await capturedTimers[0].callback()
    expect(smartcarService.getStatus).toHaveBeenCalledOnce()
  })

  it('handles fetch error gracefully - stays null', async () => {
    vi.mocked(smartcarService.getStatus).mockRejectedValue(new Error('network error'))
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { smartcarStatus, start } = useSmartcarStatus()
    await start(true)
    expect(smartcarStatus.value).toBeNull()
    // Still schedules next poll (resilient)
    expect(capturedTimers.length).toBe(1)
  })

  it('stop clears the polling timer', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(connectedCharging)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { start, stop } = useSmartcarStatus()
    await start(true)
    const scheduledId = capturedTimers[0].id
    stop()
    expect(capturedClearTimeouts).toContain(scheduledId)
  })

  it('not connected status: connected=false', async () => {
    vi.mocked(smartcarService.getStatus).mockResolvedValue(notConnected)
    const { useSmartcarStatus } = await import('../useSmartcarStatus')
    const { smartcarStatus, start } = useSmartcarStatus()
    await start(true)
    expect(smartcarStatus.value?.connected).toBe(false)
  })
})
