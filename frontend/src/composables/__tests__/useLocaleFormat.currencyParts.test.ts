import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useLocaleFormat } from '../useLocaleFormat'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ locale: ref('de'), t: (key: string) => key }) }))

// Kachelzahlen: Betrag groß, Währungssymbol als kleine Einheit dahinter
describe('currencyParts', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('trennt Betrag und Symbol, Nachkommastellen wie formatCurrency', () => {
    const { currencyParts } = useLocaleFormat()
    expect(currencyParts(10.934)).toEqual({ n: '10,93', unit: '€' })
    expect(currencyParts(120.4, { decimals: 0 })).toEqual({ n: '120', unit: '€' })
  })
})
