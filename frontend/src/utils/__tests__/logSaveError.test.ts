import { describe, it, expect } from 'vitest'
import { logSaveErrorMessage } from '../logSaveError'

const t = (key: string) => `t:${key}`
const err = (data: unknown) => ({ response: { data } })

describe('logSaveErrorMessage', () => {
  it('übersetzt eine ungültige Ladekarte statt den deutschen Backend-Text zu zeigen', () => {
    expect(logSaveErrorMessage(err({ code: 'CHARGING_PROVIDER_INVALID', message: 'Ladekarte gehört nicht zu deinem Konto.' }), t))
      .toBe('t:logform.error_card_invalid')
  })

  it('zeigt sonst die Meldung des Backends', () => {
    expect(logSaveErrorMessage(err({ message: 'Ein Eintrag mit diesem Datum existiert bereits.' }), t))
      .toBe('Ein Eintrag mit diesem Datum existiert bereits.')
  })

  it('fällt ohne Meldung auf den allgemeinen Text zurück', () => {
    expect(logSaveErrorMessage(new Error('Network Error'), t)).toBe('t:logform.error_save')
  })
})
