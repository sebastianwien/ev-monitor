import { describe, it, expect } from 'vitest'
import { convertRow, resolveIsPublicCharging } from '../tronityConverter'

const baseRow = {
  'Start Datum': '29.05.2026 16:30',
  'Geladen (kWh)': 42.427,
}

describe('resolveIsPublicCharging', () => {
  it('returns true for Typ=Öffentlich', () => {
    expect(resolveIsPublicCharging('Öffentlich', true, 11)).toBe(true)
  })

  it('returns true for Typ=öffentlich (lowercase)', () => {
    expect(resolveIsPublicCharging('öffentlich', true, 11)).toBe(true)
  })

  it('returns true for Typ=public (english)', () => {
    expect(resolveIsPublicCharging('public', true, 11)).toBe(true)
  })

  it('returns true for Typ=Supercharger', () => {
    expect(resolveIsPublicCharging('Supercharger', true, 150)).toBe(true)
  })

  it('returns false for Typ=Privat', () => {
    expect(resolveIsPublicCharging('Privat', false, 150)).toBe(false)
  })

  it('returns false for Typ=Zuhause', () => {
    expect(resolveIsPublicCharging('Zuhause', false, 11)).toBe(false)
  })

  it('returns false for Typ=home (english)', () => {
    expect(resolveIsPublicCharging('home', false, 11)).toBe(false)
  })

  it('falls back to true for DC when Typ is missing', () => {
    expect(resolveIsPublicCharging(undefined, false, 11)).toBe(true)
  })

  it('falls back to true for high power (>22kW) when Typ is missing', () => {
    expect(resolveIsPublicCharging(undefined, true, 50)).toBe(true)
  })

  it('falls back to true for DC + high power when Typ is missing', () => {
    expect(resolveIsPublicCharging(null, false, 124)).toBe(true)
  })

  it('returns undefined when no signal available', () => {
    expect(resolveIsPublicCharging(undefined, true, 11)).toBeUndefined()
  })

  it('returns undefined when Typ is empty string', () => {
    expect(resolveIsPublicCharging('', true, 11)).toBeUndefined()
  })

  it('ignores DC/kW fallback when Typ explicitly says Privat', () => {
    // DC at home wallbox should stay private
    expect(resolveIsPublicCharging('Privat', false, 50)).toBe(false)
  })
})

describe('convertRow', () => {
  it('sets is_public_charging=true from Typ=Öffentlich', () => {
    const row = { ...baseRow, 'Typ': 'Öffentlich', 'AC': false, 'Max (kW)': 124 }
    const result = convertRow(row) as Record<string, unknown>
    expect(result.is_public_charging).toBe(true)
  })

  it('sets is_public_charging=false from Typ=Privat even with DC', () => {
    const row = { ...baseRow, 'Typ': 'Privat', 'AC': false, 'Max (kW)': 50 }
    const result = convertRow(row) as Record<string, unknown>
    expect(result.is_public_charging).toBe(false)
  })

  it('sets is_public_charging=true via DC fallback when Typ missing', () => {
    const row = { ...baseRow, 'AC': false, 'Max (kW)': 27 }
    const result = convertRow(row) as Record<string, unknown>
    expect(result.is_public_charging).toBe(true)
  })

  it('sets is_public_charging=undefined when no signal', () => {
    const row = { ...baseRow, 'AC': true, 'Max (kW)': 11 }
    const result = convertRow(row) as Record<string, unknown>
    expect(result.is_public_charging).toBeUndefined()
  })

  it('returns null for missing required fields', () => {
    expect(convertRow({ 'Geladen (kWh)': 10 })).toBeNull()
    expect(convertRow({ 'Start Datum': '29.05.2026 16:30' })).toBeNull()
  })
})
