// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { createApp, h, type App } from 'vue'
import { i18n } from '../../../i18n'
import BuyerQuestions from '../BuyerQuestions.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount() {
  const onJump = vi.fn()
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(BuyerQuestions, { onJump }) })
  app.use(i18n)
  app.mount(host)
  return { host, onJump }
}

describe('BuyerQuestions', () => {
  it('a buyer asks the four questions the result answers, in its order', () => {
    const { host } = mount()
    const desktop = [...host.querySelectorAll('[data-testid="buyer-desktop"] [data-question]')].map(q => q.textContent?.trim())
    expect(desktop).toEqual(['Wie weit komme ich im Winter?', 'Wie oft muss ich laden?', 'Wie oft lade ich auf der Urlaubsfahrt?', 'Was kostet mich das im Monat?'])
    const phone = [...host.querySelectorAll('[data-testid="buyer-phone"] [data-question]')].map(q => q.textContent?.trim())
    expect(phone).toEqual(desktop)
    expect(host.querySelector('img')?.getAttribute('src')).toBe('/illustrations/buyer.svg')
  })

  it('both answers lead to the driving profile and report the jump', () => {
    const { host, onJump } = mount()
    const links = [...host.querySelectorAll('a')]
    expect(links.map(a => a.getAttribute('href'))).toEqual(['#profil', '#profil'])
    expect(links[1].textContent).toContain('Rechnen wir dir aus')
    links[0].click()
    expect(onJump).toHaveBeenCalledOnce()
  })
})
