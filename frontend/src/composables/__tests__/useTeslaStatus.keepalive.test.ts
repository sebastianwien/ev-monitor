// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const getStatus = vi.fn()
vi.mock('../../api/teslaFleetService', () => ({ default: { getStatus: () => getStatus() } }))

import { useTeslaStatus } from '../useTeslaStatus'

/** Unter KeepAlive darf eine verlassene Seite nicht im Hintergrund weiter pollen. */
describe('useTeslaStatus under KeepAlive', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    getStatus.mockReset().mockResolvedValue({ connected: true, vehicleState: 'asleep' })
  })
  afterEach(() => vi.useRealTimers())

  async function mountToggle() {
    const pinia = createPinia()
    const Page = defineComponent({
      name: 'Page',
      setup() { const { start } = useTeslaStatus(); start(true); return () => h('div') },
    })
    const show = ref(true)
    const app = createApp(defineComponent({ setup: () => () => h(KeepAlive, undefined, show.value ? h(Page) : undefined) }))
    app.use(pinia)
    app.mount(document.createElement('div'))
    await vi.advanceTimersByTimeAsync(0)
    return show
  }

  it('stops polling while deactivated', async () => {
    const show = await mountToggle()
    expect(getStatus).toHaveBeenCalledTimes(1)
    show.value = false
    await nextTick()
    await vi.advanceTimersByTimeAsync(20 * 60 * 1000)
    expect(getStatus).toHaveBeenCalledTimes(1)
  })

  it('fetches immediately and resumes polling on re-activation', async () => {
    const show = await mountToggle()
    show.value = false
    await nextTick()
    show.value = true
    await nextTick()
    await vi.advanceTimersByTimeAsync(0)
    expect(getStatus).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
    expect(getStatus).toHaveBeenCalledTimes(3)
  })
})
