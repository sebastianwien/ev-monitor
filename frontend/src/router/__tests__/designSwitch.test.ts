import { describe, it, expect } from 'vitest'
import type { RouteMeta } from 'vue-router'
import { applyDesignSwitch, showsWorkspaceFrame } from '../designSwitch'

const route = (path: string, meta: RouteMeta, name = path.slice(1)) => ({ path, name, meta: { ...meta } })
const MEMBER = { requiresAuth: true, workspaceFrame: true }

describe('applyDesignSwitch', () => {
  it('leitet zwischen Übersicht und altem Dashboard je nach Opt-in um', () => {
    expect(applyDesignSwitch(route('/dashboard', { requiresAuth: true }), true)).toBe('/story')
    expect(applyDesignSwitch(route('/story', { ...MEMBER, publicNav: true }), false)).toBe('/dashboard')
    expect(applyDesignSwitch(route('/dashboard', { requiresAuth: true }), false)).toBeUndefined()
  })

  it.each(['/logs', '/cars', '/charging-providers', '/settings', '/imports', '/coins/history', '/leaderboard'])(
    '%s bekommt den Kopf des neuen Designs nur mit Opt-in', (path) => {
      const on = route(path, MEMBER)
      applyDesignSwitch(on, true)
      expect(on.meta.publicNav).toBe(true)

      const off = route(path, MEMBER)
      applyDesignSwitch(off, false)
      expect(off.meta.publicNav).toBe(false)
    })

  it('lässt Modellseiten unabhängig vom Opt-in im öffentlichen Kopf', () => {
    const model = route('/modelle/tesla/model_3', { publicNav: true }, 'public-model')
    applyDesignSwitch(model, false)
    expect(model.meta.publicNav).toBe(true)
  })
})

describe('showsWorkspaceFrame', () => {
  it('liegt über den Bereichen der App, wenn der neue Kopf gilt', () => {
    expect(showsWorkspaceFrame(route('/settings', { ...MEMBER, publicNav: true }), true)).toBe(true)
    expect(showsWorkspaceFrame(route('/settings', { ...MEMBER, publicNav: false }), true)).toBe(false)
  })

  it('liegt über den Modellseiten für alle Eingeloggten, nie für Gäste', () => {
    for (const name of ['public-models-list', 'public-brand', 'public-model', 'public-models-compare', 'public-model-en']) {
      expect(showsWorkspaceFrame(route('/x', { publicNav: true }, name), true)).toBe(true)
      expect(showsWorkspaceFrame(route('/x', { publicNav: true }, name), false)).toBe(false)
    }
  })

  it('bleibt weg auf anderen öffentlichen Seiten (Blog, Landing)', () => {
    expect(showsWorkspaceFrame(route('/blog', { publicNav: true }, 'blog'), true)).toBe(false)
  })
})
