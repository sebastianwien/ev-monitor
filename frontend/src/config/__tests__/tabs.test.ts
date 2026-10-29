import { describe, it, expect } from 'vitest'
import { CONTEXT_TABS, CAR_TABS, WORKSPACE_TABS, WORKSPACE_LINKS, contextTabPaths, samePagerPair } from '../tabs'

describe('Tab-Konfiguration', () => {
  it('die Desktop-Leiste zeigt alle vier Ziele in der geplanten Reihenfolge', () => {
    expect(WORKSPACE_TABS.map(tab => tab.to)).toEqual([
      '/dashboard', '/logs', '/charging-providers', '/cars',
    ])
  })

  it('jeder Pager-Tab taucht in der Desktop-Leiste auf - keine Route faellt raus', () => {
    const workspacePaths = WORKSPACE_TABS.map(tab => tab.to)
    for (const tab of [...CONTEXT_TABS, ...CAR_TABS]) {
      expect(workspacePaths).toContain(tab.to)
    }
    expect(WORKSPACE_TABS).toHaveLength(CONTEXT_TABS.length + CAR_TABS.length)
  })

  it('jeder Tab hat einen i18n-Key', () => {
    for (const tab of WORKSPACE_TABS) expect(tab.labelKey).toBeTruthy()
  })

  it('die Übersicht verlinkt zusätzlich Import und Autos vergleichen, außerhalb der Wisch-Tabs', () => {
    expect(WORKSPACE_LINKS.map(l => l.to)).toEqual(['/imports', '/modelle'])
    for (const l of WORKSPACE_LINKS) expect(WORKSPACE_TABS.map(tab => tab.to)).not.toContain(l.to)
  })

  it('neues Design: die Übersicht (/story) steht im Wisch-Paar an der Stelle des Dashboards', () => {
    expect(contextTabPaths(true)).toEqual(['/story', '/logs'])
    expect(contextTabPaths(false)).toEqual(['/dashboard', '/logs'])
  })
})

// Wechsel innerhalb eines Wisch-Paars: der Pager führt den Scrollstand je Tab, nicht der Router.
describe('samePagerPair', () => {
  it('Übersicht bzw. Dashboard und Logs, Fahrzeuge und Ladekarten', () => {
    expect(samePagerPair('/story', '/logs')).toBe(true)
    expect(samePagerPair('/logs', '/dashboard')).toBe(true)
    expect(samePagerPair('/cars', '/charging-providers')).toBe(true)
  })

  it('über die Paar-Grenze, zu sich selbst oder außerhalb: nein', () => {
    expect(samePagerPair('/logs', '/cars')).toBe(false)
    expect(samePagerPair('/logs', '/logs')).toBe(false)
    expect(samePagerPair('/modelle', '/logs')).toBe(false)
  })
})
