import { describe, it, expect } from 'vitest'
import { CONTEXT_TABS, CAR_TABS, WORKSPACE_TABS } from '../tabs'

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
})
