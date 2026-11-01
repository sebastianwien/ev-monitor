// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('@/api/axios', () => ({ default: { get: vi.fn(async () => ({ data: [] })), post: vi.fn() } }))
import api from '@/api/axios'
import SiteQuestionsSheet from '../SiteQuestionsSheet.vue'
import type { SiteQuestion } from '../../../api/siteQuestionService'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const candidate = (name: string) => ({ name, maxAcKw: 22, maxDcKw: null, chargePoints: 2, address: 'Hauptstraße 1, 12345 Beispielstadt' })
const questions: SiteQuestion[] = [
  { id: 'q1', geohash: 'u0v9zq5', chargingType: 'AC', logCount: 3, lastLoggedAt: '2026-10-18T20:00:00', candidates: [candidate('Discounter'), candidate('Supermarkt')] },
  { id: 'q2', geohash: null, chargingType: 'DC', logCount: 1, lastLoggedAt: '2026-09-29T10:00:00', candidates: [candidate('Raststätte'), candidate('Autohof')] },
]

function mount(props: { questions: SiteQuestion[]; onAnswered?: () => void; onClose?: () => void }) {
  setActivePinia(createPinia())
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(SiteQuestionsSheet, props)
  app.use(createPinia())
  app.use(i18n)
  app.mount(host)
}

const flush = async () => { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }
const text = () => document.body.textContent ?? ''
const progress = () => document.querySelector('[role="progressbar"]')?.getAttribute('aria-label')
const tiles = () => [...document.querySelectorAll<HTMLButtonElement>('[data-testid="site-question-candidate"]')]

describe('SiteQuestionsSheet', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'de'
    vi.mocked(api.post).mockReset()
    vi.mocked(api.post).mockImplementation(async (url: string) =>
      ({ data: url.endsWith('/answer') ? { assigned: 3, watt: 3 } : null }) as never)
  })

  it('fragt einen Ort je Schritt und zeigt die Säulen als Kacheln', async () => {
    mount({ questions })
    await flush()

    expect(progress()).toBe('1 von 2')
    expect(text()).toContain("Welche Säule war's?")
    expect(tiles().map((t) => t.textContent)).toEqual([expect.stringContaining('Discounter'), expect.stringContaining('Supermarkt')])
  })

  it('zeigt die Zelle der Frage als Karte, ohne Ort keine Karte', async () => {
    mount({ questions: [questions[0]] })
    await flush()
    expect(document.querySelector('[data-testid="site-question-map"]')).not.toBeNull()
    app?.unmount(); document.body.innerHTML = ''
    mount({ questions: [questions[1]] })
    await flush()
    expect(document.querySelector('[data-testid="site-question-map"]')).toBeNull()
  })

  it('Antippen ordnet zu und springt zum nächsten Ort', async () => {
    const onAnswered = vi.fn()
    mount({ questions, onAnswered })
    await flush()

    tiles()[1].click()
    await flush()

    expect(api.post).toHaveBeenCalledWith('/charging-sites/questions/q1/answer', { name: 'Supermarkt' })
    expect(onAnswered).toHaveBeenCalled()
    expect(progress()).toBe('2 von 2')
    expect(tiles()[0].textContent).toContain('Raststätte')
  })

  it('"Weiß ich nicht" schließt die Frage und springt weiter, am Ende steht, was zugeordnet ist', async () => {
    mount({ questions })
    await flush()

    tiles()[0].click()
    await flush()
    ;(document.querySelector('[data-testid="site-question-unknown"]') as HTMLButtonElement).click()
    await flush()

    expect(api.post).toHaveBeenLastCalledWith('/charging-sites/questions/q2/dismiss')
    expect(text()).toContain('3 Ladungen haben ihre Säule')
    expect(text()).toContain('Neue Ladungen an diesen Säulen ordne ich ab jetzt selbst zu.')
  })

  it('schließt ohne Abschluss, wenn keine Ladung zugeordnet wurde', async () => {
    const onClose = vi.fn()
    mount({ questions: [questions[1]], onClose })
    await flush()

    ;(document.querySelector('[data-testid="site-question-unknown"]') as HTMLButtonElement).click()
    await flush()

    expect(text()).not.toContain('haben ihre Säule')
  })

  it('bleibt bei einem Fehler auf dem Ort und sagt es', async () => {
    vi.mocked(api.post).mockRejectedValueOnce(new Error('offline'))
    mount({ questions })
    await flush()

    tiles()[0].click()
    await flush()

    expect(progress()).toBe('1 von 2')
    expect(document.querySelector('[data-testid="site-question-error"]')).not.toBeNull()
  })
})
