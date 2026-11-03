// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

// Das Panel speichert selbst; hier zählt nur, was das Sheet daraus meldet
vi.mock('../../story/day/DayTripPanel.vue', () => ({
  default: defineComponent({
    emits: ['back', 'saved', 'deleted'],
    setup(_, { emit }) {
      return () => h('div', [
        h('button', { 'data-testid': 'stub-back', onClick: () => emit('back') }),
        h('button', { 'data-testid': 'stub-saved', onClick: () => emit('saved', 'edit_trip') }),
        h('button', { 'data-testid': 'stub-deleted', onClick: () => emit('deleted') }),
      ])
    },
  }),
}))
import EditTripModal from '../EditTripModal.vue'

const trip = { id: 't1', tripStartedAt: '2026-11-02T07:37:00Z', tripEndedAt: '2026-11-02T07:52:00Z', distanceKm: 3.8, socStart: 77, socEnd: 75 }
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function open() {
  const events: string[] = []
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({
    render: () => h(EditTripModal, { trip, carId: 'car-1', onChanged: () => events.push('changed'), onClose: () => events.push('close') }),
  }))
  app.use(i18n)
  app.mount(el)
  await nextTick()
  return events
}
// Das Sheet fährt erst aus, dann meldet es sich
const afterLeave = () => new Promise(r => setTimeout(r, 50))
const click = (id: string) => document.querySelector<HTMLElement>(`[data-testid="${id}"]`)!.click()

describe('EditTripModal', () => {
  it('nach Speichern: erst changed, dann close', async () => {
    const events = await open()
    click('stub-saved')
    await afterLeave()
    expect(events).toEqual(['changed', 'close'])
  })

  it('nach Löschen: erst changed, dann close', async () => {
    const events = await open()
    click('stub-deleted')
    await afterLeave()
    expect(events).toEqual(['changed', 'close'])
  })

  it('Schließen ohne Änderung: nur close, kein Neuladen', async () => {
    const events = await open()
    click('stub-back')
    await afterLeave()
    expect(events).toEqual(['close'])
  })
})
