import { describe, it, expect } from 'vitest'
import { targetTierFrom } from '../upgradeSuccessTarget'

describe('targetTierFrom', () => {
  it('kennt alle drei Tarife aus der success_url', () => {
    expect(targetTierFrom('AUTOSYNC')).toBe('AUTOSYNC')
    expect(targetTierFrom('AUTOSYNC_LIVE')).toBe('AUTOSYNC_LIVE')
    expect(targetTierFrom('SUPPORTER')).toBe('SUPPORTER')
  })

  it('alte Links ohne oder mit unbekanntem Wert bleiben AutoSync', () => {
    expect(targetTierFrom(undefined)).toBe('AUTOSYNC')
    expect(targetTierFrom('NONE')).toBe('AUTOSYNC')
    expect(targetTierFrom(['SUPPORTER'])).toBe('AUTOSYNC')
  })
})
