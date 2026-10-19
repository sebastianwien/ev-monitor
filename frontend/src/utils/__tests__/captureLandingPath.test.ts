// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { captureLandingPath, getStoredLandingPath, clearStoredLandingPath } from '../reddit-pixel'

describe('captureLandingPath', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/')
  })

  it('stores only the path of the first page, without query or hash', () => {
    window.history.replaceState(null, '', '/modelle/Tesla/Model_3?utm_source=firstev#verbrauch')

    captureLandingPath()

    expect(getStoredLandingPath()).toBe('/modelle/Tesla/Model_3')
  })

  it('keeps the first page when a later page is loaded (first touch)', () => {
    window.history.replaceState(null, '', '/modelle')
    captureLandingPath()
    window.history.replaceState(null, '', '/register')

    captureLandingPath()

    expect(getStoredLandingPath()).toBe('/modelle')
  })

  it('caps the path at the column length and can be cleared', () => {
    window.history.replaceState(null, '', '/' + 'a'.repeat(400))

    captureLandingPath()

    expect(getStoredLandingPath()).toHaveLength(200)
    clearStoredLandingPath()
    expect(getStoredLandingPath()).toBeNull()
  })
})
