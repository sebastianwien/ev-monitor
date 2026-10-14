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
  it('a choice switches the battery and closes the list', async () => {
    const { state } = mount()
    trigger().click()
    await flush()
    ;(dialog()!.querySelector('[data-variant="gtx"]') as HTMLButtonElement).click()
    await closed()
    expect(state.selected).toBe(2)
    expect(dialog()).toBeNull()
  })
})
