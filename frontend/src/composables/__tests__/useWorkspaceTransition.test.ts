import { describe, it, expect } from 'vitest'
import { slideDirection } from '../useWorkspaceTransition'

describe('slideDirection', () => {
  it('nach rechts im Tab-Streifen -> Inhalt kommt von rechts', () => {
    expect(slideDirection('/dashboard', '/logs')).toBe('nudge-left')
    expect(slideDirection('/logs', '/charging-providers')).toBe('nudge-left')
    expect(slideDirection('/dashboard', '/cars')).toBe('nudge-left')
  })

  it('nach links im Tab-Streifen -> Inhalt kommt von links', () => {
    expect(slideDirection('/cars', '/dashboard')).toBe('nudge-right')
    expect(slideDirection('/charging-providers', '/logs')).toBe('nudge-right')
  })

  it('Wege ausserhalb des Tab-Streifens animieren nicht', () => {
    expect(slideDirection('/dashboard', '/imports')).toBeUndefined()
    expect(slideDirection('/imports', '/cars')).toBeUndefined()
    expect(slideDirection('/dashboard', '/dashboard')).toBeUndefined()
  })
})
