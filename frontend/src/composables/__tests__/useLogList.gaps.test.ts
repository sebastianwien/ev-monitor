// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../i18n'

// Antworten auf /trips/gaps kommen erst, wenn der Test sie freigibt; alles andere sofort leer
const pending: { url: string; resolve: (v: { data: unknown }) => void }[] = []
vi.mock('../../api/axios', () => ({
  default: {
    get: vi.fn((url: string) => url.startsWith('/trips/gaps')
      ? new Promise(resolve => pending.push({ url, resolve }))
      : Promise.resolve({ data: [] })),
    delete: vi.fn(() => Promise.resolve({ data: null })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} })),
  },
}))

import { useLogList } from '../useLogList'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; pending.length = 0 })

const carId = ref<string | null>('c1')
function mountList() {
  let list!: ReturnType<typeof useLogList>
  carId.value = 'c1'
  app = createApp(defineComponent({
    setup() {
      list = useLogList(carId, ref([]), ref(null))
      return () => h('div')
    },
  }))
  app.use(createPinia()).use(i18n)
  app.mount(document.createElement('div'))
  return list
}
const flush = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }
const gap = (to: string) => ({ from: '2026-10-01T08:00:00Z', to, distanceKm: 10 })

describe('useLogList Strecke ohne Aufzeichnung', () => {
  it('eine ältere Antwort überschreibt nicht die Lücken des neuen Zeitraums', async () => {
    const list = mountList()
    await list.deleteTripEntry('t1')          // lädt die Lücken des bisherigen Zeitraums nach
    list.loadOlderFeed()                       // neuer Zeitraum: fetchLogs lädt sie erneut
    await flush()
    expect(pending).toHaveLength(2)

    pending[1].resolve({ data: [gap('2026-10-02T08:00:00Z')] })
    await flush()
    pending[0].resolve({ data: [gap('2026-10-09T08:00:00Z')] })
    await flush()

    expect(list.gaps.value.map(g => g.to)).toEqual(['2026-10-02T08:00:00Z'])
  })

  it('Autowechsel: die Antwort des alten Autos kommt nicht in die Liste', async () => {
    const list = mountList()
    await list.deleteTripEntry('t1')          // Anfrage für c1 läuft
    carId.value = 'c2'
    await flush()
    pending[0].resolve({ data: [gap('2026-10-09T08:00:00Z')] })
    await flush()
    expect(list.gaps.value).toEqual([])
  })

  it('Autowechsel und sofort laden: die Antwort des neuen Autos bleibt', async () => {
    const list = mountList()
    carId.value = 'c2'
    void list.fetchLogs()                     // im selben Moment, vor dem Reset-Watcher
    await flush()
    pending[pending.length - 1].resolve({ data: [gap('2026-10-02T08:00:00Z')] })
    await flush()
    expect(list.gaps.value.map(g => g.to)).toEqual(['2026-10-02T08:00:00Z'])
  })
})
