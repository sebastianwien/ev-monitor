// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, type App } from 'vue'
import ImportAccordion from '../ImportAccordion.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(order: string[]) {
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({
    render: () => h(ImportAccordion, { order }, {
      tesla: () => h('div', { 'data-testid': 'tesla' }, 'Tesla'),
      smartcar: () => h('div', { 'data-testid': 'smartcar' }, 'Smartcar'),
      eu_data_act: () => h('div', { 'data-testid': 'eu_data_act' }, 'EUDA'),
    }),
  }))
  app.mount(el)
  return el
}

const rendered = (el: HTMLElement) =>
  [...el.querySelectorAll('[data-testid]')].map(n => n.getAttribute('data-testid'))

describe('ImportAccordion', () => {
  it('rendert die Sektionen in der uebergebenen Reihenfolge - auch im DOM, nicht nur optisch', () => {
    expect(rendered(mount(['tesla', 'smartcar', 'eu_data_act']))).toEqual(['tesla', 'smartcar', 'eu_data_act'])
  })

  it('zieht die hervorgehobene Sektion nach vorn', () => {
    expect(rendered(mount(['eu_data_act', 'tesla', 'smartcar']))).toEqual(['eu_data_act', 'tesla', 'smartcar'])
  })

  it('ignoriert Schluessel ohne Slot, ohne eine leere Sektion zu hinterlassen', () => {
    const el = mount(['tesla', 'goe', 'smartcar'])
    expect(rendered(el)).toEqual(['tesla', 'smartcar'])
    expect(el.querySelectorAll('[data-testid]').length).toBe(2)
  })
})
