import { describe, it, expect } from 'vitest'
import { formatSocRange } from '../socRange'

describe('formatSocRange', () => {
  // Wie im Story-Tagesblatt: Leerzeichen um den Pfeil und vor dem Prozentzeichen, geschuetzt gegen Umbruch.
  it('zeigt Start und Ende als Spanne', () => {
    expect(formatSocRange(62, 80)).toBe('62 →\u00a080\u00a0%')
  })

  it('faellt auf den Endwert zurueck wenn der Startwert fehlt', () => {
    expect(formatSocRange(null, 80)).toBe('80\u00a0%')
    expect(formatSocRange(undefined, 80)).toBe('80\u00a0%')
  })

  it('zeigt keine Spanne wenn sich der SoC nicht geaendert hat', () => {
    expect(formatSocRange(80, 80)).toBe('80\u00a0%')
  })

  it('gibt null zurueck wenn der Endwert fehlt - ohne ihn ist die Spanne wertlos', () => {
    expect(formatSocRange(62, null)).toBeNull()
    expect(formatSocRange(null, null)).toBeNull()
    expect(formatSocRange(undefined, undefined)).toBeNull()
  })

  it('behandelt 0 % als echten Wert, nicht als fehlend', () => {
    expect(formatSocRange(0, 45)).toBe('0 →\u00a045\u00a0%')
  })

  it('rundet Nachkommastellen aus Live-Quellen auf ganze Prozent', () => {
    expect(formatSocRange(64, 64.91)).toBe('64 →\u00a065\u00a0%')
    expect(formatSocRange(63.6, 64.4)).toBe('64\u00a0%')
    expect(formatSocRange(null, 79.5)).toBe('80\u00a0%')
  })
})
