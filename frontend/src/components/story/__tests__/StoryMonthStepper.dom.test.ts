// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryMonthStepper from '../StoryMonthStepper.vue'
import type { StoryMonth } from '../../../composables/useDashboardStory'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers() })

const labelOf = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString('de-DE', { month: 'short' }).replace('.', '')

function mount(offset: number, minOffset: number, onSelect?: (o: number) => void, extra: Record<string, unknown> = {}) {
  vi.useFakeTimers({ now: new Date(2026, 9, 27, 12), toFake: ['Date'] })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryMonthStepper, { offset, minOffset, labelOf, onSelect, ...extra })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const label = (host: HTMLElement) => host.querySelector('[data-testid="story-month-label"]')!.textContent!.trim()
const prev = (host: HTMLElement) => host.querySelector('[data-testid="story-month-prev"]') as HTMLButtonElement
const next = (host: HTMLElement) => host.querySelector('[data-testid="story-month-next"]') as HTMLButtonElement

describe('StoryMonthStepper', () => {
  it('zeigt den Monat kurz, im laufenden Monat ist "weiter" aus', () => {
    const host = mount(0, -5)
    expect(label(host)).toBe('Okt')
    expect(next(host).disabled).toBe(true)
    expect(prev(host).disabled).toBe(false)
  })

  it('zurück und vor melden den Nachbarmonat, am frühesten Monat ist "zurück" aus', () => {
    const picked: number[] = []
    const host = mount(-1, -1, o => picked.push(o))
    expect(label(host)).toBe('Sep')
    expect(prev(host).disabled).toBe(true)
    next(host).click()
    expect(picked).toEqual([0])
  })

  it('ein Monat eines anderen Jahres trägt die Jahreszahl', () => {
    const host = mount(-10, -20)
    expect(label(host)).toBe('Dez 25')
    expect(host.querySelector('[data-testid="story-month-label"]')!.getAttribute('aria-label')).toBe('Dezember 2025')
  })

  it('neben dem Monatssatz nur Pfeile: der Monat bleibt für Screenreader und wird angesagt', () => {
    const host = mount(0, -5, undefined, { labelled: false })
    const el = host.querySelector('[data-testid="story-month-label"]')!
    expect(el.className).toContain('sr-only')
    // Angesagt wird der Text der Live-Region, nicht das aria-label des span
    expect(el.textContent!.trim()).toBe('Oktober 2026')
    expect(el.getAttribute('aria-live')).toBe('polite')
    expect(el.getAttribute('aria-label')).toBe('Oktober 2026')
    expect(prev(host).disabled).toBe(false)
  })

  it('ohne Angabe steht der Monat sichtbar zwischen den Pfeilen', () => {
    const host = mount(0, -5)
    expect(host.querySelector('[data-testid="story-month-label"]')!.className).not.toContain('sr-only')
  })
})
