// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick, ref, type App } from 'vue'
import { i18n } from '../../../../i18n'
import DayTripForm from '../DayTripForm.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount(values: Record<string, any>, props: Record<string, any> = {}) {
  const form = ref<Record<string, any>>({ tripStartedAt: '2026-08-16T09:00', tripEndedAt: '2026-08-16T10:00', distanceKm: 42, routeType: '', socStart: 80, socEnd: 70, loadExtras: [], ...values })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(DayTripForm, { mode: 'edit', distanceUnit: 'km', ...props, modelValue: form.value, 'onUpdate:modelValue': (v: any) => { form.value = v } }) })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  return form
}

const q = (id: string) => document.querySelector(`[data-testid="${id}"]`)

describe('DayTripForm', () => {
  it('gemessene Fahrt: Zeit und Kennzahlen statt Tabelle, nichts zum Eintippen', async () => {
    await mount({ measured: true })
    for (const id of ['trip-date', 'trip-start', 'trip-end', 'trip-distance']) expect(q(id)).toBeNull()
    expect(document.querySelectorAll('input').length).toBe(0)
    expect(q('trip-measured-time')!.textContent).toContain('09:00-10:00')
    const stats = Array.from(q('trip-measured-stats')!.children).map(c => `${c.querySelector('dt')!.textContent} ${c.querySelector('dd')!.textContent}`).join(' | ')
    expect(stats).toContain('Strecke 42 km')
    expect(stats).toContain('Dauer 1 h')
    expect(stats).toContain('Akku 80 → 70 %')
  })

  it('gemessene Fahrt ohne Akku: keine Akku-Spalte', async () => {
    await mount({ measured: true, socStart: '', socEnd: '' })
    expect(q('trip-measured-stats')!.textContent).not.toContain('Akku')
  })

  it('steht die Zeit auf der Karte, zeigt das Formular nur die Kennzahlen', async () => {
    await mount({ measured: true }, { measuredTime: false })
    expect(q('trip-measured-time')).toBeNull()
    expect(q('trip-measured-stats')).not.toBeNull()
  })

  it('von Hand angelegte Fahrt bleibt voll änderbar', async () => {
    await mount({ measured: false })
    expect(q('trip-distance')).not.toBeNull()
    expect(q('trip-measured-stats')).toBeNull()
  })

  it('Besonderheiten lassen sich mehrfach wählen und wieder abwählen', async () => {
    const form = await mount({ measured: true })
    const chip = (v: string) => q(`trip-extra-${v}`) as HTMLButtonElement
    chip('TRAILER').click(); await nextTick()
    chip('FULL_LOAD').click(); await nextTick()
    expect(form.value.loadExtras).toEqual(['TRAILER', 'FULL_LOAD'])
    expect(chip('TRAILER').getAttribute('aria-pressed')).toBe('true')
    chip('TRAILER').click(); await nextTick()
    expect(form.value.loadExtras).toEqual(['FULL_LOAD'])
  })
})
