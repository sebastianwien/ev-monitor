// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { i18n } from '../../../i18n'
import HighwayQuestion from '../HighwayQuestion.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => { i18n.global.locale.value = 'de' })

async function mount(routeType: string | null = null) {
  const form = ref<any>({ routeType })
  app = createApp(defineComponent({ render: () => h(HighwayQuestion, { modelValue: form.value, 'onUpdate:modelValue': (v: any) => { form.value = v } }) }))
  app.use(i18n)
  app.mount(document.body.appendChild(document.createElement('div')))
  await nextTick()
  const q = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  return { form, q }
}

describe('HighwayQuestion: "War das eine Autobahnfahrt?"', () => {
  it('"Ja" setzt Autobahn und die Karte verschwindet', async () => {
    const { form, q } = await mount()
    expect(q('highway-question')?.textContent).toContain('War das eine Autobahnfahrt?')
    q('highway-yes')!.click(); await nextTick()
    expect(form.value.routeType).toBe('HIGHWAY')
    expect(q('highway-question')).toBeNull()
  })
  it('"Nein" bietet Stadt oder Gemischt an, Gemischt wird übernommen', async () => {
    const { form, q } = await mount()
    q('highway-no')!.click(); await nextTick()
    expect(q('highway-yes')).toBeNull()
    expect(q('highway-other-CITY')).not.toBeNull()
    q('highway-other-COMBINED')!.click(); await nextTick()
    expect(form.value.routeType).toBe('COMBINED')
  })
  it('mit gesetztem Streckentyp keine Karte', async () => {
    const { q } = await mount('CITY')
    expect(q('highway-question')).toBeNull()
  })
})
