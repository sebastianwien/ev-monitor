// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, ref, defineComponent, h, type App } from 'vue'
import { i18n } from '../../../i18n'
import RulerInput from '../RulerInput.vue'
import { activeRuler } from '../rulerState'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; activeRuler.value = null })

function mount(fields: { id: string; autofocus?: boolean }[]) {
  const host = document.createElement('div'); document.body.appendChild(host)
  const values = fields.map(() => ref<number | null>(null))
  app = createApp(defineComponent({
    setup: () => () => h('div', fields.map((f, i) => h(RulerInput, {
      id: f.id, unit: 'kWh', label: f.id, min: 0, max: 120, step: 0.1, autofocus: f.autofocus,
      modelValue: values[i].value, 'onUpdate:modelValue': (v: number | null | undefined) => { values[i].value = v ?? null },
    }))),
  }))
  app.use(i18n); app.mount(host)
  return { host, values }
}

describe('RulerInput', () => {
  it('Tastatureingabe landet im Modell, leer ist null', async () => {
    const { host, values } = mount([{ id: 'kwh' }])
    const input = host.querySelector('#kwh') as HTMLInputElement
    input.value = '32.4'; input.dispatchEvent(new Event('input')); await nextTick()
    expect(values[0].value).toBe(32.4)
    input.value = ''; input.dispatchEvent(new Event('input')); await nextTick()
    expect(values[0].value).toBeNull()
  })

  it('nur das angetippte Feld zeigt seinen Maßstab', async () => {
    const { host } = mount([{ id: 'a', autofocus: true }, { id: 'b' }])
    await nextTick()
    // Der Maßstab sitzt in einem Collapse: zugeklappt ist er inert, bis die Animation ihn ausblendet.
    // jsdom kennt die inert-Eigenschaft nicht, Vue schreibt dann das Attribut mit "true"/"false".
    const shown = () => [...host.querySelectorAll('[role="slider"]')].map(s => !s.closest('[inert]:not([inert="false"])'))
    expect(shown()).toEqual([true, false])
    ;(host.querySelector('#b') as HTMLInputElement).dispatchEvent(new Event('focus'))
    await nextTick()
    expect(shown()).toEqual([false, true])
    expect(activeRuler.value).toBe('b')
  })

  it('Pfeiltasten auf dem Maßstab ändern den Wert um einen Schritt, mit Shift um zehn', async () => {
    const { host, values } = mount([{ id: 'kwh', autofocus: true }])
    await nextTick()
    const slider = host.querySelector('[role="slider"]') as HTMLElement
    slider.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' })); await nextTick()
    expect(values[0].value).toBe(0.1)
    slider.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', shiftKey: true })); await nextTick()
    expect(values[0].value).toBe(1.1)
    slider.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' })); await nextTick()
    expect(values[0].value).toBe(1)
  })
})
