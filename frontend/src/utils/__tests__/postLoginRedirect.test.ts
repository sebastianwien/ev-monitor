// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { rememberReturnPath, takeReturnPath } from '../postLoginRedirect'

describe('postLoginRedirect', () => {
  beforeEach(() => sessionStorage.clear())

  it('returns the remembered in-app path once', () => {
    rememberReturnPath('/modelle/Volkswagen/ID.3?utm_source=firstev')
    expect(takeReturnPath()).toBe('/modelle/Volkswagen/ID.3?utm_source=firstev')
    expect(takeReturnPath()).toBeNull()
  })

  it.each(['//evil.example.com', 'https://evil.example.com', 'javascript:alert(1)', 'modelle', '/\\evil.example.com'])(
    'never returns a foreign target: %s', (target) => {
      sessionStorage.setItem('postLoginRedirect', target)
      expect(takeReturnPath()).toBeNull()
    })
})
