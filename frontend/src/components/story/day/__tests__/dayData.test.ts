import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const get = vi.fn()
vi.mock('../../../../api/axios', () => ({ default: { get: (...a: unknown[]) => get(...a) } }))

import { loadDay, prefetchDay, PREFETCH_TTL_MS } from '../dayData'

beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: [url] }))
  vi.useFakeTimers()
})
afterEach(() => { vi.useRealTimers() })

describe('Tagesblatt vorladen', () => {
  it('lädt Ladungen, Fahrten und Lücken des Tages', async () => {
    const d = await loadDay('c1', '2026-11-02')
    expect(get.mock.calls.map(c => c[0])).toEqual(['/logs', '/trips', '/trips/gaps'])
    expect(get.mock.calls[0][1].params.carId).toBe('c1')
    expect(d).toEqual({ logs: ['/logs'], trips: ['/trips'], gaps: ['/trips/gaps'] })
  })

  it('der Fingerdruck startet die Requests, das Öffnen nimmt sie und fragt nicht noch einmal', async () => {
    prefetchDay('c1', '2026-11-02')
    prefetchDay('c1', '2026-11-02')
    expect(get).toHaveBeenCalledTimes(3)
    await loadDay('c1', '2026-11-02')
    expect(get).toHaveBeenCalledTimes(3)
    // genommen ist genommen: ein Neuladen (z. B. nach Speichern) fragt frisch
    await loadDay('c1', '2026-11-02')
    expect(get).toHaveBeenCalledTimes(6)
  })

  it('ein anderer Tag oder ein anderes Auto lädt selbst', async () => {
    prefetchDay('c1', '2026-11-07')
    await loadDay('c1', '2026-11-03')
    await loadDay('c2', '2026-11-07')
    expect(get).toHaveBeenCalledTimes(9)
    await loadDay('c1', '2026-11-07')
  })

  it('ein alter Vorabruf gilt nicht mehr', async () => {
    prefetchDay('c1', '2026-11-04')
    vi.advanceTimersByTime(PREFETCH_TTL_MS + 1)
    await loadDay('c1', '2026-11-04')
    expect(get).toHaveBeenCalledTimes(6)
  })

  it('Lücken sind optional: scheitert der Abruf, bleibt der Tag ohne', async () => {
    get.mockImplementation((url: string) => url === '/trips/gaps' ? Promise.reject(new Error('x')) : Promise.resolve({ data: [] }))
    expect((await loadDay('c1', '2026-11-05')).gaps).toEqual([])
  })

  it('scheitert der Vorabruf erst nach dem Öffnen, fragt das Öffnen selbst noch einmal', async () => {
    let fail: (e: Error) => void = () => {}
    get.mockImplementationOnce(() => new Promise((_, reject) => { fail = reject }))
    prefetchDay('c1', '2026-11-08')
    const opened = loadDay('c1', '2026-11-08')
    get.mockImplementation((url: string) => Promise.resolve({ data: [url] }))
    fail(new Error('offline'))
    await expect(opened).resolves.toEqual({ logs: ['/logs'], trips: ['/trips'], gaps: ['/trips/gaps'] })
  })

  it('ein gescheiterter Vorabruf wird beim Öffnen nicht wiederverwendet', async () => {
    get.mockImplementationOnce(() => Promise.reject(new Error('offline')))
    prefetchDay('c1', '2026-11-06')
    for (let i = 0; i < 10; i++) await Promise.resolve()
    get.mockImplementation((url: string) => Promise.resolve({ data: [url] }))
    await expect(loadDay('c1', '2026-11-06')).resolves.toEqual({ logs: ['/logs'], trips: ['/trips'], gaps: ['/trips/gaps'] })
  })
})
