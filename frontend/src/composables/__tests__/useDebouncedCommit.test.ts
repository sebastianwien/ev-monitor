// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp } from 'vue'
import { useDebouncedCommit } from '../useDebouncedCommit'

const mount = (commit: (v: number) => void, ms: number) => {
  let api!: ReturnType<typeof useDebouncedCommit<number>>
  const app = createApp({ setup() { api = useDebouncedCommit(commit, ms); return () => null } })
  app.mount(document.createElement('div'))
  return { api, app }
}

describe('useDebouncedCommit', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('drei Ziffern nacheinander ergeben einen Commit mit dem letzten Wert', () => {
    const commit = vi.fn()
    const { api } = mount(commit, 300)
    api.set(1)
    vi.advanceTimersByTime(100)
    api.set(12)
    vi.advanceTimersByTime(100)
    api.set(120)
    vi.advanceTimersByTime(299)
    expect(commit).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(commit).toHaveBeenCalledTimes(1)
    expect(commit).toHaveBeenCalledWith(120)
  })

  it('flush (Blur, Enter) übergibt sofort und der Timer feuert danach nicht mehr', () => {
    const commit = vi.fn()
    const { api } = mount(commit, 300)
    api.set(40)
    api.flush()
    expect(commit).toHaveBeenCalledWith(40)
    vi.advanceTimersByTime(400)
    expect(commit).toHaveBeenCalledTimes(1)
  })

  it('flush ohne offenen Wert tut nichts', () => {
    const commit = vi.fn()
    const { api } = mount(commit, 300)
    api.flush()
    expect(commit).not.toHaveBeenCalled()
  })

  it('Unmount verwirft den offenen Wert', () => {
    const commit = vi.fn()
    const { api, app } = mount(commit, 300)
    api.set(7)
    app.unmount()
    vi.advanceTimersByTime(400)
    expect(commit).not.toHaveBeenCalled()
  })
})
