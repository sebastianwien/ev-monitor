// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { resolveCiSkin, applyCiSkin, setCiSkin, ciSkin, isSkinRoute, CI_SKIN_KEY } from '../useCiSkin'

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

  it('kennt die Kanten-Varianten k1 bis k3', () => {
    expect(resolveCiSkin('k1', null)).toBe('k1')
    expect(resolveCiSkin(undefined, 'k3')).toBe('k3')
  })
})

describe('applyCiSkin', () => {
  const story = { name: 'story', query: {} }
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.ci
  })

  it('setzt data-ci nur auf /story und /logs und merkt sich die Wahl', () => {
    applyCiSkin({ name: 'story', query: { ci: 'v1' } })
    expect(document.documentElement.dataset.ci).toBe('v1')
    expect(localStorage.getItem(CI_SKIN_KEY)).toBe('v1')

    applyCiSkin({ name: 'cars', query: {} })
    expect(document.documentElement.dataset.ci).toBeUndefined()

    applyCiSkin({ name: 'logs', query: {} })
    expect(document.documentElement.dataset.ci).toBe('v1')

    applyCiSkin(story)
    expect(document.documentElement.dataset.ci).toBe('v1')
  })

  it('?ci=off entfernt Skin und gemerkte Wahl', () => {
    localStorage.setItem(CI_SKIN_KEY, 'v1')
    applyCiSkin({ name: 'story', query: { ci: 'off' } })
    expect(document.documentElement.dataset.ci).toBeUndefined()
    expect(localStorage.getItem(CI_SKIN_KEY)).toBeNull()
  })

  it('setCiSkin schaltet von außen um (Admin-Umschalter) und merkt sich die Wahl', () => {
    setCiSkin('v1', { name: 'story', query: {} })
    expect(document.documentElement.dataset.ci).toBe('v1')
    expect(ciSkin.value).toBe('v1')
    expect(localStorage.getItem(CI_SKIN_KEY)).toBe('v1')

    setCiSkin(null, { name: 'story', query: {} })
    expect(document.documentElement.dataset.ci).toBeUndefined()
    expect(ciSkin.value).toBeNull()
    expect(localStorage.getItem(CI_SKIN_KEY)).toBeNull()
  })

  it('isSkinRoute kennt die Routen des Skins', () => {
    expect(isSkinRoute('story')).toBe(true)
    expect(isSkinRoute('cars')).toBe(false)
  })
})
