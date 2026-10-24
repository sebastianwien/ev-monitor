// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createApp, defineComponent, h } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'

const getCars = vi.fn()
vi.mock('../../api/carService', () => ({ carService: { getCars: () => getCars() } }))
vi.mock('../../api/teslaFleetService', () => ({ default: { getStatus: vi.fn().mockResolvedValue(null) } }))

import { useCarForm } from '../useCarForm'
import { useCarStore } from '../../stores/car'

const flush = () => new Promise(r => setTimeout(r, 0))
const car = (id: string) => ({ id, brand: 'Tesla', model: 'MODEL_3' }) as never

function setup(prepare?: () => Promise<unknown>) {
  let form!: ReturnType<typeof useCarForm>
  const pinia = createPinia()
  setActivePinia(pinia)
  return (prepare ?? (async () => {}))().then(() => mountForm(pinia, f => { form = f })).then(() => form)
}

function mountForm(pinia: ReturnType<typeof createPinia>, take: (f: ReturnType<typeof useCarForm>) => void) {
  let form!: ReturnType<typeof useCarForm>
  const app = createApp(defineComponent({ setup() { form = useCarForm(); return () => h('div') } }))
  app.use(pinia).use(createI18n({ legacy: false, locale: 'de', messages: { de: {} }, missingWarn: false }))
  app.mount(document.createElement('div'))
  take(form)
}

/** Die Fahrzeugseite zeigt sofort, was da ist: Fotos und Tesla-Status blockieren sie nicht. */
describe('useCarForm.fetchCars', () => {
  beforeEach(() => getCars.mockReset())

  it('shows cached cars at once and refreshes them in the background', async () => {
    const form = await setup()
    const store = useCarStore()
    getCars.mockResolvedValueOnce([car('a')])
    await store.getCars()
    let resolveFresh!: (v: unknown) => void
    getCars.mockImplementationOnce(() => new Promise(r => { resolveFresh = r }))

    const done = form.fetchCars(async () => {}, () => {})
    await flush()
    expect(form.loading.value).toBe(false)
    expect(form.cars.value.map(c => c.id)).toEqual(['a'])

    resolveFresh([car('a'), car('b')])
    await done
    expect(form.cars.value.map(c => c.id)).toEqual(['a', 'b'])
  })

  it('shows the loading state only while nothing is known yet', async () => {
    const form = await setup()
    let resolve!: (v: unknown) => void
    getCars.mockImplementationOnce(() => new Promise(r => { resolve = r }))

    const done = form.fetchCars(async () => {}, () => {})
    await flush()
    expect(form.loading.value).toBe(true)
    resolve([car('a')])
    await done
    expect(form.loading.value).toBe(false)
  })

  it('does not wait for the photos', async () => {
    const form = await setup()
    getCars.mockResolvedValueOnce([car('a')])
    const photos = new Promise<void>(() => {})

    await form.fetchCars(() => photos, () => {})
    expect(form.loading.value).toBe(false)
  })

  it('renders cached cars on the very first frame', async () => {
    getCars.mockResolvedValueOnce([car('a')])
    const form = await setup(() => useCarStore().getCars())
    expect(form.loading.value).toBe(false)
    expect(form.cars.value.map(c => c.id)).toEqual(['a'])
  })
})
