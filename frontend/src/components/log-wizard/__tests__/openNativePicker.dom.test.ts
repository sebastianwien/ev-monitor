// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { openNativePicker } from '../useTimePick'

describe('openNativePicker', () => {
  it('ohne showPicker (ältere Browser) fokussiert und klickt das Feld', () => {
    const el = document.createElement('input')
    el.showPicker = () => { throw new Error('not supported') }
    const focus = vi.spyOn(el, 'focus'), click = vi.spyOn(el, 'click')
    openNativePicker(el)
    expect(focus).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
  })
  it('ohne Feld passiert nichts', () => {
    expect(() => openNativePicker(null)).not.toThrow()
  })
})
