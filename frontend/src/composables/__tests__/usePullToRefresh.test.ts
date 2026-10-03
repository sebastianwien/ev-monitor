import { describe, it, expect } from 'vitest'
import { dampPull, shouldTriggerRefresh, PULL_THRESHOLD_PX } from '../usePullToRefresh'

describe('usePullToRefresh - reine Pull-Logik', () => {
  describe('dampPull', () => {
    it('ignoriert Aufwaerts-/Null-Bewegung (kein Pull)', () => {
      expect(dampPull(0)).toBe(0)
      expect(dampPull(-120)).toBe(0)
    })

    it('daempft die Roh-Distanz (Gummiband-Effekt)', () => {
      // Abwaertsziehen wird gedaempft zurueckgegeben, nie 1:1
      expect(dampPull(100)).toBeGreaterThan(0)
      expect(dampPull(100)).toBeLessThan(100)
    })

    it('ist monoton: mehr Ziehen => groesserer Indikator', () => {
      expect(dampPull(200)).toBeGreaterThan(dampPull(100))
    })
  })

  describe('shouldTriggerRefresh', () => {
    it('loest unterhalb der Schwelle NICHT aus', () => {
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX - 1)).toBe(false)
    })

    it('loest ab der Schwelle aus', () => {
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX)).toBe(true)
      expect(shouldTriggerRefresh(PULL_THRESHOLD_PX + 50)).toBe(true)
    })
  })
})

import { vi } from 'vitest'
import { registerSoftReload, runReload } from '../usePullToRefresh'

describe('usePullToRefresh - weicher Reload', () => {
  it('nutzt den harten Reload, wenn nichts angemeldet ist', async () => {
    const hard = vi.fn()
    await runReload(hard)
    expect(hard).toHaveBeenCalledTimes(1)
  })

  it('ruft die angemeldete Funktion statt des harten Reloads', async () => {
    const soft = vi.fn().mockResolvedValue(undefined)
    const hard = vi.fn()
    const off = registerSoftReload(soft)
    await runReload(hard)
    expect(soft).toHaveBeenCalledTimes(1)
    expect(hard).not.toHaveBeenCalled()
    off()
    await runReload(hard)
    expect(hard).toHaveBeenCalledTimes(1)
  })

  it('eine veraltete Abmeldung loescht keine neuere Anmeldung', async () => {
    const first = vi.fn()
    const second = vi.fn()
    const offFirst = registerSoftReload(first)
    registerSoftReload(second)
    offFirst()
    const hard = vi.fn()
    await runReload(hard)
    expect(second).toHaveBeenCalledTimes(1)
    expect(hard).not.toHaveBeenCalled()
    registerSoftReload(second)()
  })
})
