// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { nextField } from '../keyboardNav'

afterEach(() => { document.body.innerHTML = '' })

describe('nextField', () => {
  it('springt zum nächsten Zahlenfeld und überspringt versteckte, gesperrte und zugeklappte', () => {
    document.body.innerHTML = `<div id="c">
      <input id="a" type="number">
      <input id="hidden" type="number" class="sr-only">
      <input id="off" type="number" disabled>
      <div inert="true"><input id="closed" type="number"></div>
      <input id="search" type="text">
      <input id="b" type="number">
    </div>`
    const c = document.getElementById('c')!
    expect(nextField(c, document.getElementById('a')!)?.id).toBe('b')
  })

  it('nach dem letzten Feld kommt nichts', () => {
    document.body.innerHTML = '<div id="c"><input id="a" type="number"><input id="b" type="number"></div>'
    const c = document.getElementById('c')!
    expect(nextField(c, document.getElementById('b')!)).toBeNull()
  })

  it('ein offenes Collapse (inert="false" aus jsdom) zählt als sichtbar', () => {
    document.body.innerHTML = '<div id="c"><input id="a" type="number"><div inert="false"><input id="b" type="number"></div></div>'
    const c = document.getElementById('c')!
    expect(nextField(c, document.getElementById('a')!)?.id).toBe('b')
  })

  it('Ausweichfelder (Preis je kWh neben dem Gesamtbetrag) überspringt Enter, aus ihnen geht es normal weiter', () => {
    document.body.innerHTML = '<div id="c"><input id="cost" type="number"><input id="alt" type="number" data-enter-skip><input id="b" type="number"></div>'
    const c = document.getElementById('c')!
    expect(nextField(c, document.getElementById('cost')!)?.id).toBe('b')
    expect(nextField(c, document.getElementById('alt')!)?.id).toBe('b')
  })
})
