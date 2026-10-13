// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive, type App } from 'vue'
import { i18n } from '../../../i18n'
import VariantPicker, { type VariantOption } from '../VariantPicker.vue'

const OPTIONS: VariantOption[] = [
  { key: 'neo', label: 'Neo Pure', battery: '50 kWh', range: '230 km', prospect: 'Prospekt 380 km' },
  { key: 'pro', label: 'Pro', battery: '59 kWh', range: '280 km', prospect: 'Prospekt 390 km' },
  { key: 'gtx', label: 'GTX', battery: '79 kWh', range: null, prospect: 'Prospekt 540 km' },
]

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount() {
  const state = reactive({ selected: 1 })
  const Host = defineComponent({
    setup: () => () => h(VariantPicker, { options: OPTIONS, modelValue: state.selected, 'onUpdate:modelValue': (i: number) => { state.selected = i } }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n)
  app.mount(host)
  return { host, state }
}

const flush = async () => { await nextTick(); await nextTick() }
/** The sheet slides out: wait for the leave transition (a frame in jsdom) */
const closed = async () => { await flush(); await new Promise(r => setTimeout(r, 60)); await flush() }
const trigger = () => document.querySelector('[data-testid="variant-picker"]') as HTMLButtonElement
const dialog = () => document.querySelector('[role="dialog"]') as HTMLElement | null

describe('VariantPicker', () => {
  it('names the chosen battery on the button, the list stays closed', () => {
    mount()
    expect(trigger().textContent).toContain('Pro, 59 kWh')
    expect(trigger().getAttribute('aria-expanded')).toBe('false')
    expect(dialog()).toBeNull()
  })

  it('opens the list with every battery, the chosen one pressed and focused', async () => {
    mount()
    trigger().click()
    await flush()
    const items = [...dialog()!.querySelectorAll('button[data-variant]')]
    expect(items.map(b => b.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      'Neo Pure 50 kWh 230 km Prospekt 380 km',
      'Pro 59 kWh 280 km Prospekt 390 km',
      'GTX 79 kWh Prospekt 540 km',
    ])
    expect(items[1].getAttribute('aria-pressed')).toBe('true')
    expect(document.activeElement).toBe(items[1])
  })

  it('a choice switches the battery, closes the list and gives the focus back', async () => {
    const { state } = mount()
    trigger().click()
    await flush()
    ;(dialog()!.querySelector('[data-variant="gtx"]') as HTMLButtonElement).click()
    await closed()
    expect(state.selected).toBe(2)
    expect(dialog()).toBeNull()
    expect(document.activeElement).toBe(trigger())
  })

  it('Escape closes without a change', async () => {
    const { state } = mount()
    trigger().click()
    await flush()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await closed()
    expect(dialog()).toBeNull()
    expect(state.selected).toBe(1)
  })
})
