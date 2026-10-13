// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive, type App } from 'vue'
import { i18n } from '../../../i18n'
import ProfileSlider from '../ProfileSlider.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(value: number | null) {
  const state = reactive({ value, commits: [] as number[] })
  const Host = defineComponent({
    setup: () => () => h(ProfileSlider, {
      id: 'story-commuteDays', testKey: 'commuteDays', label: 'Pendeltage pro Woche',
      min: 1, max: 5, step: 1, start: 5, value: state.value, format: (v: number) => `${v} Tage`,
      onCommit: (v: number) => { state.commits.push(v); state.value = v },
    }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n)
  app.mount(host)
  return { host, state, input: host.querySelector('input') as HTMLInputElement }
}

describe('ProfileSlider', () => {
  it('open: says so, no value chosen', () => {
    const { host } = mount(null)
    expect(host.querySelector('output')!.textContent?.trim()).toBe('noch offen')
  })

  it('a tap on the thumb without moving answers with the start value (5 days sits on the edge)', async () => {
    const { input, state } = mount(null)
    input.dispatchEvent(new Event('pointerup'))
    await nextTick()
    expect(state.commits).toEqual([5])
  })

  it('dragging away and back to the start value still answers on release', async () => {
    const { input, state } = mount(null)
    input.value = '3'
    input.dispatchEvent(new Event('input'))
    input.value = '5'
    input.dispatchEvent(new Event('input'))
    input.dispatchEvent(new Event('pointerup'))
    await nextTick()
    expect(state.commits).toEqual([5])
  })

  it('a regular drag answers once, not again on pointerup', async () => {
    const { input, state } = mount(null)
    input.value = '3'
    input.dispatchEvent(new Event('input'))
    input.dispatchEvent(new Event('change'))
    input.dispatchEvent(new Event('pointerup'))
    await nextTick()
    expect(state.commits).toEqual([3])
  })

  it('tabbing past an open slider is no answer', async () => {
    const { input, state } = mount(null)
    input.dispatchEvent(new Event('blur'))
    await nextTick()
    expect(state.commits).toEqual([])
  })

  it('an answered slider does not answer again on a mere tap', async () => {
    const { input, state } = mount(4)
    input.dispatchEvent(new Event('pointerup'))
    await nextTick()
    expect(state.commits).toEqual([])
  })
})
