// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, type App } from 'vue'
import { i18n } from '../../../i18n'
import WizardShell from '../WizardShell.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(withPrimarySlot: boolean) {
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp({
    render: () => h(WizardShell, { step: 1, question: 'Wo?', canProceed: false, primaryLabel: 'Weiter' },
      withPrimarySlot ? { default: () => h('p'), primary: () => h('button', { 'data-testid': 'suggestion-accept' }, 'Übernehmen') } : { default: () => h('p') }),
  })
  app.use(i18n).mount(el)
  return el
}

describe('WizardShell Fußleiste', () => {
  it('zeigt ohne Slot "Weiter"', () => {
    const el = mount(false)
    expect(el.querySelector('footer [data-testid="wizard-next"]')).not.toBeNull()
  })
  it('ersetzt "Weiter" durch den Slot primary (Vorschlag in Daumenreichweite)', () => {
    const el = mount(true)
    expect(el.querySelector('footer [data-testid="suggestion-accept"]')).not.toBeNull()
    expect(el.querySelector('[data-testid="wizard-next"]')).toBeNull()
  })
})
