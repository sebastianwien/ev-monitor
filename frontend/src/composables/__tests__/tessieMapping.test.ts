import { describe, it, expect } from 'vitest'
import { isMappingComplete } from '../tessieMapping'

describe('isMappingComplete', () => {
  it('returns false when no VINs are selected', () => {
    expect(isMappingComplete([], {})).toBe(false)
  })

  it('returns false when at least one selected VIN has no car id', () => {
    const mapping = { 'VIN-A': 'car-1' }
    expect(isMappingComplete(['VIN-A', 'VIN-B'], mapping)).toBe(false)
  })

  it('returns false when a mapped car id is an empty string', () => {
    const mapping = { 'VIN-A': 'car-1', 'VIN-B': '' }
    expect(isMappingComplete(['VIN-A', 'VIN-B'], mapping)).toBe(false)
  })

  it('returns false when the assigned car id is undefined', () => {
    const mapping: Record<string, string | undefined> = { 'VIN-A': 'car-1', 'VIN-B': undefined }
    expect(isMappingComplete(['VIN-A', 'VIN-B'], mapping)).toBe(false)
  })

  it('returns true when every selected VIN has a non-empty car id', () => {
    const mapping = { 'VIN-A': 'car-1', 'VIN-B': 'car-2' }
    expect(isMappingComplete(['VIN-A', 'VIN-B'], mapping)).toBe(true)
  })

  it('ignores extra entries in the mapping that are not selected', () => {
    const mapping = { 'VIN-A': 'car-1', 'VIN-OTHER': 'car-99' }
    expect(isMappingComplete(['VIN-A'], mapping)).toBe(true)
  })
})
