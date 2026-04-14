import { describe, it, expect, afterEach } from 'vitest'
import { detectCountryFromLanguage, isDetectionAmbiguous } from '../useCountryDetection'

function mockLanguages(languages: string[]) {
  Object.defineProperty(navigator, 'languages', {
    value: languages,
    configurable: true,
  })
}

afterEach(() => {
  Object.defineProperty(navigator, 'languages', {
    value: ['en-US'],
    configurable: true,
  })
})

describe('detectCountryFromLanguage', () => {
  it('detects DE for de-DE', () => {
    mockLanguages(['de-DE'])
    expect(detectCountryFromLanguage()).toBe('DE')
  })

  it('detects DE for plain de', () => {
    mockLanguages(['de'])
    expect(detectCountryFromLanguage()).toBe('DE')
  })

  it('detects GB for en-GB', () => {
    mockLanguages(['en-GB'])
    expect(detectCountryFromLanguage()).toBe('GB')
  })

  it('detects US for en-US', () => {
    mockLanguages(['en-US'])
    expect(detectCountryFromLanguage()).toBe('US')
  })

  it('detects AT for de-AT', () => {
    mockLanguages(['de-AT'])
    expect(detectCountryFromLanguage()).toBe('AT')
  })

  it('detects NO for nb-NO', () => {
    mockLanguages(['nb-NO'])
    expect(detectCountryFromLanguage()).toBe('NO')
  })

  it('detects SE for sv-SE', () => {
    mockLanguages(['sv-SE'])
    expect(detectCountryFromLanguage()).toBe('SE')
  })

  it('picks first matching language from list', () => {
    mockLanguages(['en-GB', 'de-DE'])
    expect(detectCountryFromLanguage()).toBe('GB')
  })

  it('falls through to second language if first has no match', () => {
    mockLanguages(['ja-JP', 'de-DE'])
    expect(detectCountryFromLanguage()).toBe('DE')
  })

  it('returns null for unsupported language', () => {
    mockLanguages(['ja-JP'])
    expect(detectCountryFromLanguage()).toBeNull()
  })

  it('returns null for empty language list', () => {
    mockLanguages([])
    expect(detectCountryFromLanguage()).toBeNull()
  })
})

describe('isDetectionAmbiguous', () => {
  it('returns true when primary language is en-US (could be UK or US)', () => {
    mockLanguages(['en-US'])
    expect(isDetectionAmbiguous()).toBe(true)
  })

  it('returns true when primary language is en (no region)', () => {
    mockLanguages(['en'])
    expect(isDetectionAmbiguous()).toBe(true)
  })

  it('returns true when no language matches the map at all', () => {
    mockLanguages(['ja-JP'])
    expect(isDetectionAmbiguous()).toBe(true)
  })

  it('returns true for empty language list', () => {
    mockLanguages([])
    expect(isDetectionAmbiguous()).toBe(true)
  })

  it('returns false for de-DE (unambiguous)', () => {
    mockLanguages(['de-DE'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('returns false for en-GB (unambiguous)', () => {
    mockLanguages(['en-GB'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('returns false for nb-NO (unambiguous)', () => {
    mockLanguages(['nb-NO'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('returns false for sv-SE (unambiguous)', () => {
    mockLanguages(['sv-SE'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('returns false for da-DK (unambiguous)', () => {
    mockLanguages(['da-DK'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('returns false for nl-NL (unambiguous)', () => {
    mockLanguages(['nl-NL'])
    expect(isDetectionAmbiguous()).toBe(false)
  })

  it('is not ambiguous even if second language is en-US (only primary matters)', () => {
    mockLanguages(['de-DE', 'en-US'])
    expect(isDetectionAmbiguous()).toBe(false)
  })
})
