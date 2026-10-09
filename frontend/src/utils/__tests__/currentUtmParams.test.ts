// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { currentUtmParams } from '../reddit-pixel'

describe('currentUtmParams', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/')
  })

  it('prefers the URL over the stored first-visit params', () => {
    localStorage.setItem('utm-params', JSON.stringify({ source: 'reddit', medium: 'cpc', campaign: 'x' }))
    window.history.replaceState(null, '', '/modelle/Volkswagen/ID.3?utm_source=firstev&utm_medium=tile')

    expect(currentUtmParams()).toEqual({ source: 'firstev', medium: 'tile', campaign: '' })
  })

  it('falls back to the stored params', () => {
    localStorage.setItem('utm-params', JSON.stringify({ source: 'firstev', medium: 'forum', campaign: 'vorstellung' }))

    expect(currentUtmParams()).toEqual({ source: 'firstev', medium: 'forum', campaign: 'vorstellung' })
  })

  it('is null without any campaign', () => {
    expect(currentUtmParams()).toBeNull()
  })
})
