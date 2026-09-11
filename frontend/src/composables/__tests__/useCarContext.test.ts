// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createApp, defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

const initCars = vi.fn().mockResolvedValue(undefined)
vi.mock('../useDashboardStats', () => ({
  useDashboardStats: () => ({
    selectedCarId: ref<string | null>(null),
    cars: ref([]),
    initCars,
  }),
}))
vi.mock('../useLogList', () => ({
  useLogList: () => ({ logs: ref([]) }),
}))
vi.mock('../../api/carService', () => ({
  carService: { getCars: vi.fn().mockResolvedValue([]) },
}))

import { provideCarContext } from '../useCarContext'
import { useCarStore } from '../../stores/car'

const Layout = defineComponent({
  name: 'CarContextLayout',
  setup() { provideCarContext(); return () => h('div') },
})

describe('provideCarContext under KeepAlive', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    initCars.mockClear()
  })

  async function mountToggle() {
    const show = ref(true)
    createApp(defineComponent({
      setup: () => () => h(KeepAlive, undefined, show.value ? h(Layout) : undefined),
    })).mount(document.createElement('div'))
    await nextTick()
    return show
  }

  it('reloads cars on re-activation when the store was invalidated', async () => {
    const store = useCarStore()
    await store.getCars()
    const show = await mountToggle()
    expect(initCars).toHaveBeenCalledTimes(1)

    show.value = false
    await nextTick()
    store.invalidateCars()
    show.value = true
    await nextTick()

    expect(initCars).toHaveBeenCalledTimes(2)
  })

  it('runs initCars only once on first mount even though the store is empty', async () => {
    await mountToggle()
    expect(initCars).toHaveBeenCalledTimes(1)
  })

  it('does not reload on re-activation when cars are still fresh', async () => {
    const store = useCarStore()
    await store.getCars()
    const show = await mountToggle()
    show.value = false
    await nextTick()
    show.value = true
    await nextTick()
    expect(initCars).toHaveBeenCalledTimes(1)
  })
})
