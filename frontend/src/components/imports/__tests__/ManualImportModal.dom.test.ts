// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

const post = vi.fn((_url: string, _body: unknown) => Promise.resolve({ data: { imported: 1, skipped: 0, errors: 0 } }))
vi.mock('../../../api/axios', () => ({
  default: { post: (url: string, body: unknown) => post(url, body) },
}))
import ManualImportModal from '../ManualImportModal.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => { post.mockClear() })

function mount(props: Record<string, unknown> = {}) {
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({ render: () => h(ManualImportModal, { carId: 'car-1', ...props }) }))
  app.use(i18n)
  app.mount(el)
  return el
}

const tab = (el: HTMLElement, type: string) =>
  el.querySelector<HTMLButtonElement>(`[data-testid="import-type-${type}"]`)!
const template = (el: HTMLElement) => el.querySelector('pre')!.textContent ?? ''
const textarea = (el: HTMLElement) => el.querySelector('textarea')!
const importBtn = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('[data-testid="import-submit"]')!

describe('ManualImportModal', () => {
  it('startet mit Ladevorgängen und zeigt die Sessions-Vorlage', () => {
    const el = mount()
    expect(tab(el, 'sessions').getAttribute('aria-selected')).toBe('true')
    expect(template(el)).toContain('date,kwh')
  })

  it('wechselt auf Fahrten, tauscht Vorlage und Titel', async () => {
    const el = mount()
    const sessionsTitle = el.querySelector('h2')!.textContent
    tab(el, 'trips').click()
    await nextTick()
    expect(tab(el, 'trips').getAttribute('aria-selected')).toBe('true')
    expect(template(el)).toContain('started_at,ended_at')
    expect(template(el)).not.toContain('kwh')
    const tripsTitle = el.querySelector('h2')!.textContent
    expect(tripsTitle).toBeTruthy()
    expect(tripsTitle).not.toBe(sessionsTitle)
  })

  it('setzt eingegebene Daten beim Typwechsel zurück', async () => {
    const el = mount()
    textarea(el).value = 'date,kwh\n2025-01-01,10'
    textarea(el).dispatchEvent(new Event('input'))
    await nextTick()
    tab(el, 'trips').click()
    await nextTick()
    expect(textarea(el).value).toBe('')
  })

  it('schickt Fahrten an /import/trips, Ladevorgänge an /import/sessions', async () => {
    const el = mount()
    textarea(el).value = 'date,kwh\n2025-01-01,10'
    textarea(el).dispatchEvent(new Event('input'))
    await nextTick()
    importBtn(el).click()
    await nextTick(); await nextTick()
    expect(post).toHaveBeenLastCalledWith('/import/sessions', expect.objectContaining({ carId: 'car-1', format: 'csv' }))

    tab(el, 'trips').click()
    await nextTick()
    textarea(el).value = 'started_at,ended_at\n2025-01-01T08:00:00+01:00,2025-01-01T09:00:00+01:00'
    textarea(el).dispatchEvent(new Event('input'))
    await nextTick()
    importBtn(el).click()
    await nextTick(); await nextTick()
    expect(post).toHaveBeenLastCalledWith('/import/trips', { carId: 'car-1', format: 'csv', data: expect.stringContaining('started_at') })
  })

  it('öffnet direkt auf Fahrten, wenn initialType gesetzt ist', () => {
    const el = mount({ initialType: 'trips' })
    expect(tab(el, 'trips').getAttribute('aria-selected')).toBe('true')
  })
})
