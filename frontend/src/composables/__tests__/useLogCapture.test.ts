import { describe, it, expect } from 'vitest'
import { opensAsSheet } from '../useLogCapture'

const route = (name: string | null, matched = 1) => ({ name, matched: Array.from({ length: matched }) })

describe('opensAsSheet', () => {
  it('ein Link auf /erfassen aus der App heraus öffnet das Sheet statt der Seite', () => {
    expect(opensAsSheet(route('log-form'), route('statistics'))).toBe(true)
  })

  it('der erste Aufruf von /erfassen (Lesezeichen, Reload) zeigt die Seite', () => {
    expect(opensAsSheet(route('log-form'), route(null, 0))).toBe(false)
  })

  it('andere Ziele bleiben Seitenwechsel', () => {
    expect(opensAsSheet(route('statistics'), route('log-form'))).toBe(false)
  })
})
