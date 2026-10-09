import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useLocaleFormat } from '../useLocaleFormat'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ locale: ref('de'), t: (key: string) => key }) }))

// Reichweiten in der Rangliste: auf volle Zehner abgerundet, in der Anzeigeeinheit
describe('formatDistance step', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('rundet mit step 10 auf den nächstkleineren Zehner ab', () => {
    const { formatDistance } = useLocaleFormat()
    expect(formatDistance(231, { showUnit: false, step: 10 })).toBe('230')
    expect(formatDistance(239, { showUnit: false, step: 10 })).toBe('230')
    expect(formatDistance(240, { showUnit: false, step: 10 })).toBe('240')
  })

  it('ohne step bleibt es beim Runden auf ganze Werte', () => {
    const { formatDistance } = useLocaleFormat()
    expect(formatDistance(231.6, { showUnit: false })).toBe('232')
  })
})
