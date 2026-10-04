// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import PillSwitch from '../PillSwitch.vue'

/** Kippschalter mit zwei Hälften: beide sichtbar und tippbar, der Knopf gleitet unter die aktive */
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

describe('PillSwitch', () => {
  it('zeigt beide Optionen, markiert die aktive und schaltet per Tap', async () => {
    const model = ref<'a' | 'b'>('a')
    app = createApp(defineComponent({ render: () => h(PillSwitch, {
      modelValue: model.value, 'onUpdate:modelValue': (v: string) => { model.value = v as 'a' | 'b' },
      label: 'Einheit', options: [{ value: 'a', label: '€', testid: 'opt-a' }, { value: 'b', label: 'ct/kWh', testid: 'opt-b' }] }) }))
    app.mount(document.body.appendChild(document.createElement('div')))
    const q = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)!
    expect(document.body.querySelector('[role="radiogroup"]')!.getAttribute('aria-label')).toBe('Einheit')
    expect(q('opt-a').getAttribute('aria-checked')).toBe('true')
    expect(q('opt-b').textContent).toContain('ct/kWh')
    q('opt-b').click(); await nextTick()
    expect(model.value).toBe('b')
    expect(q('opt-b').getAttribute('aria-checked')).toBe('true')
  })
})
