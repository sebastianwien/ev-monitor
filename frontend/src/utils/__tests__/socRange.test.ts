import { describe, it, expect } from 'vitest'
import { formatSocRange } from '../socRange'

describe('formatSocRange', () => {
  it('zeigt Start und Ende als Spanne', () => {
    expect(formatSocRange(62, 80)).toBe('62→80%')
  })

  it('faellt auf den Endwert zurueck wenn der Startwert fehlt', () => {
    expect(formatSocRange(null, 80)).toBe('80%')
    expect(formatSocRange(undefined, 80)).toBe('80%')
  })

  it('zeigt keine Spanne wenn sich der SoC nicht geaendert hat', () => {
    expect(formatSocRange(80, 80)).toBe('80%')
  })

  it('gibt null zurueck wenn der Endwert fehlt - ohne ihn ist die Spanne wertlos', () => {
    expect(formatSocRange(62, null)).toBeNull()
    expect(formatSocRange(null, null)).toBeNull()
    expect(formatSocRange(undefined, undefined)).toBeNull()
  })

  it('behandelt 0 % als echten Wert, nicht als fehlend', () => {
    expect(formatSocRange(0, 45)).toBe('0→45%')
  })
})
