// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, ref, type App } from 'vue'
import { i18n } from '../../../i18n'
import CarSwitch from '../CarSwitch.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const tesla = { id: 'c1', displayName: 'Tesla Model 3', brand: 'TESLA', model: 'MODEL_3', trim: 'LR RWD', licensePlate: 'B AB 1234E' }
const vw = { id: 'c2', displayName: 'VW ID.4', brand: 'VOLKSWAGEN', model: 'ID_4', trim: null, licensePlate: null }

function mount(cars: object[], charging = false) {
  const selected = ref('c1')
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    components: { CarSwitch },
    setup: () => ({ cars, selected, charging }),
    template: '<CarSwitch v-model="selected" :cars="cars" :car-image-urls="{}" :charging="charging" />',
  })
  app.use(i18n)
  app.mount(host)
  return { host, selected }
}

describe('CarSwitch', () => {
  it('one car: names it with trim and plate, nothing to open', () => {
    const { host } = mount([tesla])
    expect(host.textContent).toContain('Model 3')
    expect(host.textContent).toContain('LR RWD')
    expect(host.textContent).toContain('B AB 1234E')
    expect(host.querySelector('button')).toBeNull()
  })

  it('several cars: the name drops down a list in place, choosing a car selects it and closes', async () => {
    const { host, selected } = mount([tesla, vw])
    const trigger = host.querySelector<HTMLButtonElement>('[data-testid="car-switch"]')!
    expect(trigger.getAttribute('aria-haspopup')).toBe('listbox')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    trigger.click()
    await nextTick()
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(document.querySelector('[data-testid="car-switch-backdrop"]')).not.toBeNull()
    const list = document.querySelector('[role="listbox"]')!
    const options = list.querySelectorAll<HTMLButtonElement>('[data-testid="car-switch-option"]')
    expect(options).toHaveLength(2)
    expect(options[0].getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(options[0])
    options[1].click()
    await nextTick()
    expect(selected.value).toBe('c2')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
  })

  it('keyboard: arrows walk the list, Escape closes and returns to the name', async () => {
    const { host } = mount([tesla, vw])
    const trigger = host.querySelector<HTMLButtonElement>('[data-testid="car-switch"]')!
    trigger.click()
    await nextTick()
    const options = document.querySelectorAll<HTMLButtonElement>('[data-testid="car-switch-option"]')
    options[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    expect(document.activeElement).toBe(options[1])
    options[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger)
  })

  it('says when the car is charging', () => {
    const { host } = mount([tesla], true)
    expect(host.querySelector('[data-testid="car-switch-charging"]')?.textContent).toContain('Lädt')
  })
})
