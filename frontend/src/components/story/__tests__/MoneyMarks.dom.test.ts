// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import MoneyMarks from '../MoneyMarks.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

describe('MoneyMarks', () => {
  it('umschließt nur Geldbeträge samt Währung, der Text bleibt gleich', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const text = 'vorgestern · AC · 45,5 kWh · 18,20 €'
    app = createApp(MoneyMarks, { text })
    app.mount(host)
    expect([...host.querySelectorAll('.ci-money')].map(e => e.textContent)).toEqual(['18,20 €'])
    expect(host.textContent).toBe(text)
  })
})
