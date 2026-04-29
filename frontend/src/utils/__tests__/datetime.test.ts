import { describe, it, expect } from 'vitest'
import { datetimeLocalToUtcIso } from '../datetime'

describe('datetimeLocalToUtcIso', () => {
  it('gibt String im Format YYYY-MM-DDTHH:MM:00 zurück', () => {
    const result = datetimeLocalToUtcIso('2026-04-29T22:14')
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$/)
  })

  it('Roundtrip: UTC zurück zu lokal ergibt den Ausgangswert', () => {
    const input = '2026-04-29T22:14'
    const utcIso = datetimeLocalToUtcIso(input)
    // utcIso ist UTC-Zeit ohne Z - als UTC parsen für Roundtrip-Prüfung
    const d = new Date(utcIso + 'Z')
    const pad = (n: number) => String(n).padStart(2, '0')
    const backToLocal = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    expect(backToLocal).toBe(input)
  })

  it('behandelt Mitternacht korrekt', () => {
    const result = datetimeLocalToUtcIso('2026-04-30T00:14')
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$/)
    const d = new Date(result + 'Z')
    const pad = (n: number) => String(n).padStart(2, '0')
    const backToLocal = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    expect(backToLocal).toBe('2026-04-30T00:14')
  })
})
