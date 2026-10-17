import { describe, it, expect } from 'vitest'
import { isModelsRoute, navDirection } from '../pageTransitions'

describe('isModelsRoute', () => {
  it('list, brand, compare and model page in every market', () => {
    for (const name of ['public-models-list', 'public-models-compare', 'public-brand', 'public-model',
      'public-models-list-en', 'public-models-compare-en', 'public-brand-gb', 'public-model-se']) {
      expect(isModelsRoute(name), name).toBe(true)
    }
  })

  it('no other route, no route at all', () => {
    for (const name of ['landing', 'dashboard', 'public-models-preview', 'public-modelx', undefined, null]) {
      expect(isModelsRoute(name), String(name)).toBe(false)
    }
  })
})

describe('navDirection', () => {
  it('deeper is forward: list to model page, list to compare, brand to model page', () => {
    expect(navDirection('public-models-list', 'public-model')).toBe('forward')
    expect(navDirection('public-models-list', 'public-models-compare')).toBe('forward')
    expect(navDirection('public-brand-en', 'public-model-en')).toBe('forward')
  })

  it('up is back: model page to list or brand, compare to list', () => {
    expect(navDirection('public-model', 'public-models-list')).toBe('back')
    expect(navDirection('public-model', 'public-brand')).toBe('back')
    expect(navDirection('public-models-compare', 'public-models-list')).toBe('back')
  })

  it('same level has no direction: model to model, compare to brand', () => {
    expect(navDirection('public-model', 'public-model')).toBeUndefined()
    expect(navDirection('public-models-compare', 'public-brand')).toBeUndefined()
  })
})
