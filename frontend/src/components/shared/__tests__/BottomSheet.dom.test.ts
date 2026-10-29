// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import BottomSheet from '../BottomSheet.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; document.body.style.overflow = '' })

async function mount(locked: boolean) {
  const closed = { n: 0 }
  const el = document.createElement('div')
  document.body.appendChild(el)
  app = createApp(defineComponent({
    render: () => h(BottomSheet, { label: 'Test', locked, onClose: () => { closed.n++ } }, { default: () => h('p', { 'data-testid': 'content' }, 'Inhalt') }),
  }))
  app.mount(el)
  await nextTick()
  return closed
}
// Die Schließ-Animation läuft erst durch, dann meldet das Sheet close
const afterLeave = () => new Promise(r => setTimeout(r, 50))

const backdrop = () => document.querySelector<HTMLElement>('[role="dialog"] > .absolute')!

describe('BottomSheet', () => {
  it('schließt per Klick auf die Abdunklung', async () => {
    const closed = await mount(false)
    backdrop().click()
    await afterLeave()
    expect(closed.n).toBe(1)
  })

  it('bleibt offen, solange es gesperrt ist (laufender Import)', async () => {
    const closed = await mount(true)
    backdrop().click()
    await afterLeave()
    expect(closed.n).toBe(0)
    expect(document.querySelector('[data-testid="content"]')).not.toBeNull()
  })

  it('die Seite dahinter scrollt nicht, solange ein Sheet offen ist, auch gestapelt', async () => {
    document.body.style.overflow = 'auto'
    const open = { a: ref(true), b: ref(false) }
    const el = document.createElement('div')
    document.body.appendChild(el)
    app = createApp(defineComponent({
      render: () => [
        open.a.value ? h(BottomSheet, { key: 'a', label: 'A' }) : null,
        open.b.value ? h(BottomSheet, { key: 'b', label: 'B', stacked: true }) : null,
      ],
    }))
    app.mount(el)
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    open.b.value = true
    await nextTick()
    open.a.value = false
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    open.b.value = false
    await nextTick()
    expect(document.body.style.overflow).toBe('auto')
  })

  describe('nach unten ziehen wie eine Karte (Handy)', () => {
    const panel = () => document.querySelector<HTMLElement>('.sheet-panel')!
    /** Touch an `el` mit Fingerpositionen in px (y), alle 16 ms ein Schritt. */
    function drag(el: HTMLElement, ys: number[], xs: number[] = ys.map(() => 100)) {
      const fire = (type: string, i: number) => {
        const e = new Event(type, { bubbles: true, cancelable: true })
        Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: xs[i], clientY: ys[i] }] })
        Object.defineProperty(e, 'timeStamp', { value: i * 16 })
        el.dispatchEvent(e)
        return e
      }
      fire('touchstart', 0)
      const moves = ys.slice(1).map((_, i) => fire('touchmove', i + 1))
      fire('touchend', ys.length - 1)
      return moves
    }
    async function mountWith(content: () => ReturnType<typeof h>, locked = false) {
      const closed = { n: 0 }
      const el = document.createElement('div')
      document.body.appendChild(el)
      app = createApp(defineComponent({
        render: () => h(BottomSheet, { label: 'Test', locked, onClose: () => { closed.n++ } }, { default: content }),
      }))
      app.mount(el)
      await nextTick()
      return closed
    }
    const body = () => h('div', { 'data-testid': 'body' }, 'Inhalt')

    it('ein Zug nach unten über die Schwelle schließt, der Inhalt scrollt dabei nicht', async () => {
      const closed = await mountWith(body)
      const moves = drag(document.querySelector<HTMLElement>('[data-testid="body"]')!, [100, 130, 180, 230])
      expect(moves.every(e => e.defaultPrevented)).toBe(true)
      await afterLeave()
      expect(closed.n).toBe(1)
    })

    it('ein kurzer Zug federt zurück', async () => {
      const closed = await mountWith(body)
      drag(document.querySelector<HTMLElement>('[data-testid="body"]')!, [100, 105, 110])
      await afterLeave()
      expect(closed.n).toBe(0)
      expect(panel().style.transform).toBe('translateY(0)')
    })

    it('ein zweiter Finger mitten im Zug bricht ab, das Sheet federt zurück', async () => {
      const closed = await mountWith(body)
      const target = document.querySelector<HTMLElement>('[data-testid="body"]')!
      const fire = (type: string, touches: Array<{ clientX: number; clientY: number }>) => {
        const e = new Event(type, { bubbles: true, cancelable: true })
        Object.defineProperty(e, 'touches', { value: touches })
        target.dispatchEvent(e)
      }
      fire('touchstart', [{ clientX: 100, clientY: 100 }])
      fire('touchmove', [{ clientX: 100, clientY: 150 }])
      fire('touchstart', [{ clientX: 100, clientY: 150 }, { clientX: 200, clientY: 300 }])
      fire('touchend', [{ clientX: 200, clientY: 300 }])
      fire('touchend', [])
      await afterLeave()
      expect(closed.n).toBe(0)
      expect(panel().style.transform).toBe('translateY(0)')
    })

    it('nach oben und seitlich bleibt nativ', async () => {
      const closed = await mountWith(body)
      const target = document.querySelector<HTMLElement>('[data-testid="body"]')!
      expect(drag(target, [300, 250, 150]).some(e => e.defaultPrevented)).toBe(false)
      expect(drag(target, [100, 104, 108, 112], [100, 160, 220, 280]).some(e => e.defaultPrevented)).toBe(false)
      await afterLeave()
      expect(closed.n).toBe(0)
    })

    it('gescrollter Inhalt scrollt erst zurück, statt das Sheet zu ziehen', async () => {
      const closed = await mountWith(() => h('div', { 'data-testid': 'list', style: 'overflow-y: auto' }, [h('p', { 'data-testid': 'row' }, 'Zeile')]))
      const list = document.querySelector<HTMLElement>('[data-testid="list"]')!
      Object.defineProperty(list, 'scrollHeight', { value: 800 })
      Object.defineProperty(list, 'clientHeight', { value: 300 })
      list.scrollTop = 120
      expect(drag(document.querySelector<HTMLElement>('[data-testid="row"]')!, [100, 160, 220, 280]).some(e => e.defaultPrevented)).toBe(false)
      await afterLeave()
      expect(closed.n).toBe(0)
    })

    it('Karte, Regler, gesperrtes Sheet und ein Feld mit Fokus ziehen nicht', async () => {
      let closed = await mountWith(() => h('div', { 'data-no-sheet-drag': '' }, [h('span', { 'data-testid': 'map' }, 'Karte')]))
      drag(document.querySelector<HTMLElement>('[data-testid="map"]')!, [100, 160, 220, 280])
      await afterLeave()
      expect(closed.n).toBe(0)
      app!.unmount(); app = null
      closed = await mountWith(() => h('input', { type: 'range', 'data-testid': 'slider', style: 'touch-action: none' }))
      drag(document.querySelector<HTMLElement>('[data-testid="slider"]')!, [100, 160, 220, 280])
      await afterLeave()
      expect(closed.n).toBe(0)
      app!.unmount(); app = null
      closed = await mountWith(body, true)
      drag(document.querySelector<HTMLElement>('[data-testid="body"]')!, [100, 160, 220, 280])
      await afterLeave()
      expect(closed.n).toBe(0)
      app!.unmount(); app = null
      // Beim Tippen in ein Feld wirft ein Wisch nichts weg
      closed = await mountWith(() => h('div', [h('input', { 'data-testid': 'field' }), h('p', { 'data-testid': 'row' }, 'Zeile')]))
      document.querySelector<HTMLInputElement>('[data-testid="field"]')!.focus()
      drag(document.querySelector<HTMLElement>('[data-testid="row"]')!, [100, 160, 220, 280])
      await afterLeave()
      expect(closed.n).toBe(0)
    })
  })

  describe('minimieren statt schließen (Erfassen mit Eingaben)', () => {
    const panel = () => document.querySelector<HTMLElement>('.sheet-panel')!
    function drag(el: HTMLElement, ys: number[]) {
      const fire = (type: string, i: number) => {
        const e = new Event(type, { bubbles: true, cancelable: true })
        Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: 100, clientY: ys[i] }] })
        Object.defineProperty(e, 'timeStamp', { value: i * 16 })
        el.dispatchEvent(e)
      }
      fire('touchstart', 0)
      ys.slice(1).forEach((_, i) => fire('touchmove', i + 1))
      fire('touchend', ys.length - 1)
    }
    async function mountMin(minimized = ref(false)) {
      const calls = { close: 0, minimize: 0 }
      const el = document.createElement('div')
      document.body.appendChild(el)
      app = createApp(defineComponent({
        render: () => h(BottomSheet, {
          label: 'Test', minimizable: true, minimized: minimized.value,
          onClose: () => { calls.close++ }, onMinimize: () => { calls.minimize++; minimized.value = true },
        }, { default: () => h('div', { 'data-testid': 'body' }, 'Inhalt') }),
      }))
      app.mount(el)
      await nextTick()
      return calls
    }

    it('ein Zug nach unten minimiert, der Inhalt bleibt im DOM', async () => {
      const calls = await mountMin()
      drag(document.querySelector<HTMLElement>('[data-testid="body"]')!, [100, 130, 180, 230])
      await afterLeave()
      expect(calls).toEqual({ close: 0, minimize: 1 })
      expect(document.querySelector('[data-testid="body"]')).not.toBeNull()
      // Der Zug gibt das Panel frei, die Lage bestimmt jetzt der minimierte Zustand
      expect(panel().style.transform).toBe('')
    })

    it('ein Tipp auf die Abdunklung minimiert ebenfalls', async () => {
      const calls = await mountMin()
      backdrop().click()
      await afterLeave()
      expect(calls).toEqual({ close: 0, minimize: 1 })
    })

    it('minimiert scrollt die Seite dahinter wieder, aufgeklappt nicht', async () => {
      document.body.style.overflow = 'auto'
      const minimized = ref(false)
      await mountMin(minimized)
      expect(document.body.style.overflow).toBe('hidden')
      minimized.value = true
      await nextTick()
      expect(document.body.style.overflow).toBe('auto')
      expect(document.querySelector('[role="dialog"]')!.getAttribute('aria-modal')).toBe('false')
      minimized.value = false
      await nextTick()
      expect(document.body.style.overflow).toBe('hidden')
    })
  })
})
