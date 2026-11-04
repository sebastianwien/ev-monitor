// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { resolveCiSkin, applyCiSkin, CI_SKIN_KEY } from '../useCiSkin'

describe('resolveCiSkin', () => {
  it('schaltet mit ?ci=v1 ein und mit ?ci=off aus', () => {
    expect(resolveCiSkin('v1', null)).toBe('v1')
    expect(resolveCiSkin('off', 'v1')).toBeNull()
  })

  it('nimmt ohne Parameter die gemerkte Wahl, unbekannte Werte zählen nicht', () => {
    expect(resolveCiSkin(undefined, 'v1')).toBe('v1')
    expect(resolveCiSkin(undefined, null)).toBeNull()
    expect(resolveCiSkin('v9', null)).toBeNull()
    expect(resolveCiSkin(undefined, 'xyz')).toBeNull()
  })
})

describe('applyCiSkin', () => {
  const story = { name: 'story', query: {} }
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.ci
  })

  it('setzt data-ci nur auf /story und merkt sich die Wahl', () => {
    applyCiSkin({ name: 'story', query: { ci: 'v1' } })
    expect(document.documentElement.dataset.ci).toBe('v1')
    expect(localStorage.getItem(CI_SKIN_KEY)).toBe('v1')

    applyCiSkin({ name: 'logs', query: {} })
    expect(document.documentElement.dataset.ci).toBeUndefined()

    applyCiSkin(story)
    expect(document.documentElement.dataset.ci).toBe('v1')
  })

  it('?ci=off entfernt Skin und gemerkte Wahl', () => {
    localStorage.setItem(CI_SKIN_KEY, 'v1')
    applyCiSkin({ name: 'story', query: { ci: 'off' } })
    expect(document.documentElement.dataset.ci).toBeUndefined()
    expect(localStorage.getItem(CI_SKIN_KEY)).toBeNull()
  })
})
