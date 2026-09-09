// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { spritMonitorErrorKey } from '../spritMonitorService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

describe('spritMonitorErrorKey', () => {
  it('meldet "nicht erreichbar", wenn das Backend den Code UNREACHABLE liefert', () => {
    expect(spritMonitorErrorKey({ response: { data: { code: 'UNREACHABLE' } } }))
      .toBe('spritmonitor.err_unreachable')
  })

  it('meldet "nicht erreichbar", wenn gar keine Antwort ankommt (Netzwerkfehler/Timeout)', () => {
    expect(spritMonitorErrorKey({ message: 'Network Error' })).toBe('spritmonitor.err_unreachable')
  })

  it('meldet "Token ungültig" bei Code TOKEN_INVALID', () => {
    expect(spritMonitorErrorKey({ response: { data: { code: 'TOKEN_INVALID' } } }))
      .toBe('spritmonitor.err_token_invalid')
  })

  it('faellt bei unbekanntem Fehler auf den allgemeinen API-Fehler zurueck', () => {
    expect(spritMonitorErrorKey({ response: { status: 500, data: { error: 'boom' } } }))
      .toBe('spritmonitor.err_api')
  })
})
