// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick, ref, type App } from 'vue'
import { i18n } from '../../../i18n'
import StationsSheet from '../StationsSheet.vue'
import type { Snap } from '../../../utils/snapSheet'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount() {
  const snap = ref<Snap>('peek')
  const host = document.createElement('div')
  host.style.height = '800px'
  document.body.appendChild(host)
  app = createApp({ render: () => h(StationsSheet, { label: 'Säulen', peek: 190, snap: snap.value, 'onUpdate:snap': (v: Snap) => { snap.value = v } }) })
  app.use(i18n)
  app.mount(host)
  return { host, snap }
}

/** jsdom kennt keinen PointerEvent-Konstruktor: ein MouseEvent mit pointerId reicht für die Handler */
function pointer(el: Element, kind: string, y: number, t: number) {
  const e = new MouseEvent(kind, { bubbles: true, clientY: y, button: 0 })
  Object.defineProperty(e, 'pointerId', { value: 1 })
  Object.defineProperty(e, 'timeStamp', { value: t })
  el.dispatchEvent(e)
}

describe('StationsSheet', () => {
  it('Tipp auf den Griff: klein, halb, ganz, wieder klein', async () => {
    const { host, snap } = mount()
    const grip = host.querySelector<HTMLButtonElement>('[data-testid="stations-sheet-grip"]')!

    grip.click(); await nextTick()
    expect(snap.value).toBe('half')
    grip.click(); await nextTick()
    expect(snap.value).toBe('full')
    expect(grip.getAttribute('aria-label')).toBe('Liste verkleinern')
    grip.click(); await nextTick()
    expect(snap.value).toBe('peek')
  })

  it('nach einem Zug zählt der folgende Klick des Zugs nicht, die nächste Taste am Griff schon', async () => {
    const { host, snap } = mount()
    const grip = host.querySelector<HTMLButtonElement>('[data-testid="stations-sheet-grip"]')!
    const handle = grip.parentElement!

    pointer(handle, 'pointerdown', 600, 0)
    pointer(handle, 'pointermove', 500, 50)
    pointer(handle, 'pointermove', 300, 100)
    pointer(handle, 'pointerup', 300, 100)
    await nextTick()
    const afterDrag = snap.value
    expect(afterDrag).not.toBe('peek')

    grip.click() // Klick, den der Browser direkt nach dem Loslassen schickt
    await nextTick()
    expect(snap.value).toBe(afterDrag)

    await new Promise(r => setTimeout(r, 0))
    grip.click() // Enter oder Leertaste später
    await nextTick()
    expect(snap.value).not.toBe(afterDrag)
  })

  it('kommt der Klick nach dem Zug nicht am Griff an (Pointer-Capture), wirkt die nächste Taste trotzdem', async () => {
    const { host, snap } = mount()
    const grip = host.querySelector<HTMLButtonElement>('[data-testid="stations-sheet-grip"]')!
    const handle = grip.parentElement!

    pointer(handle, 'pointerdown', 600, 0)
    pointer(handle, 'pointermove', 400, 50)
    pointer(handle, 'pointerup', 300, 100)
    await nextTick()
    const afterDrag = snap.value

    await new Promise(r => setTimeout(r, 0))
    grip.click()
    await nextTick()
    expect(snap.value).not.toBe(afterDrag)
  })
})
