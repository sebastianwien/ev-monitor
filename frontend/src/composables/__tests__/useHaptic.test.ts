// @vitest-environment jsdom
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { haptic, onHapticClick } from '../useHaptic'

let now = 0
const vibrate = vi.fn(() => true)
beforeEach(() => {
  now += 1000 // jeder Test weit hinter der Sperre des vorigen
  vi.spyOn(performance, 'now').mockImplementation(() => now)
  Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true })
  vibrate.mockClear()
  document.addEventListener('click', onHapticClick, { capture: true })
})
afterEach(() => {
  document.removeEventListener('click', onHapticClick, { capture: true })
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

function html(markup: string) {
  document.body.innerHTML = markup
  return document.body.firstElementChild as HTMLElement
}
const pointer = (el: Element, type: string) => el.dispatchEvent(new Event(type, { bubbles: true }))

describe('Haptik beim Tippen', () => {
  it('Aufsetzen allein und Aufsetzen mit Scroll ticken nicht', () => {
    const button = html('<button><span>Los</span></button>')
    pointer(button.firstElementChild!, 'pointerdown')
    pointer(button.firstElementChild!, 'pointercancel')
    expect(vibrate).not.toHaveBeenCalled()
  })

  it('ein Tap auf Button, 3D-Knopf oder v-haptic tickt genau einmal', () => {
    for (const markup of ['<button><span>Los</span></button>', '<a class="btn-3d"><span>Los</span></a>', '<div data-haptic><span>Los</span></div>']) {
      vibrate.mockClear()
      now += 1000
      ;(html(markup).firstElementChild as HTMLElement).click()
      expect(vibrate, markup).toHaveBeenCalledTimes(1)
    }
  })

  it('ein Tap neben Knöpfe tickt nicht', () => {
    html('<div><span>Text</span></div>').click()
    expect(vibrate).not.toHaveBeenCalled()
  })

  it('der verzögerte Nachklick von .btn-3d-delay tickt nicht ein zweites Mal', () => {
    const button = html('<button class="btn-3d btn-3d-delay">Los</button>')
    const delayed = new MouseEvent('click', { bubbles: true, cancelable: true })
    ;(delayed as MouseEvent & { __delayed?: boolean }).__delayed = true
    button.dispatchEvent(delayed)
    expect(vibrate).not.toHaveBeenCalled()
  })

  it('Android mit ausgeschalteter Vibration bleibt still, der Ton ist nur für Geräte ohne vibrate', () => {
    const audio = vi.fn()
    vi.stubGlobal('AudioContext', audio)
    vibrate.mockReturnValueOnce(false)
    haptic()
    expect(audio).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('globaler Listener und @click="haptic()" derselben Berührung geben einen Tick', () => {
    const button = html('<button>Los</button>')
    button.addEventListener('click', () => haptic())
    button.click()
    expect(vibrate).toHaveBeenCalledTimes(1)
    now += 60
    haptic()
    expect(vibrate).toHaveBeenCalledTimes(2)
  })
})
