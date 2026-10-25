// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { i18n } from '../../../i18n'
import FloatingActionButton, { nextExpanded } from '../FloatingActionButton.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(props: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(FloatingActionButton, props)
  app.use(i18n)
  app.mount(host)
  return host
}

describe('nextExpanded: Pille klappt beim Runterscrollen ein, beim Hochscrollen aus', () => {
  it('oben auf der Seite immer ausgeklappt', () => {
    expect(nextExpanded(400, 40, false)).toBe(true)
  })
  it('runter um mehr als ein paar Pixel: eingeklappt', () => {
    expect(nextExpanded(300, 340, true)).toBe(false)
  })
  it('hoch um mehr als ein paar Pixel: ausgeklappt', () => {
    expect(nextExpanded(600, 560, false)).toBe(true)
  })
  it('kleines Zittern ändert nichts', () => {
    expect(nextExpanded(300, 303, true)).toBe(true)
    expect(nextExpanded(300, 297, false)).toBe(false)
  })
})

describe('FloatingActionButton', () => {
  it('rund: nur Plus, Beschriftung für Screenreader', () => {
    const host = mount()
    const btn = host.querySelector('button')!
    expect(btn.getAttribute('aria-label')).toBe('Ladevorgang erfassen')
    expect(host.querySelector('[data-testid="fab-label"]')).toBeNull()
  })

  it('Pille: Plus und "Erfassen" sichtbar, Klick meldet sich', () => {
    let clicks = 0
    const host = mount({ extended: true, onClick: () => { clicks++ } })
    expect(host.querySelector('[data-testid="fab-label"]')!.textContent).toBe('Erfassen')
    host.querySelector('button')!.click()
    expect(clicks).toBe(1)
  })
})
