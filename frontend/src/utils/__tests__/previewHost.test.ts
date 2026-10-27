import { describe, it, expect } from 'vitest'
import { isPreviewHost, mainAppOrigin, previewRedirect } from '../previewHost'

describe('previewHost', () => {
  it('erkennt die Vorschau-Adresse am Präfix', () => {
    expect(isPreviewHost('vorschau.ev-monitor.net')).toBe(true)
    expect(isPreviewHost('ev-monitor.net')).toBe(false)
    expect(isPreviewHost('localhost')).toBe(false)
  })

  it('Hauptadresse: Präfix weg, sonst der eigene Ursprung', () => {
    expect(mainAppOrigin('vorschau.ev-monitor.net', 'https:')).toBe('https://ev-monitor.net')
    expect(mainAppOrigin('ev-monitor.net', 'https:')).toBe('https://ev-monitor.net')
  })

  it('schickt nur Eingeloggte ohne Einladung auf dieselbe Seite der Hauptadresse', () => {
    const base = { hostname: 'vorschau.ev-monitor.net', protocol: 'https:', authenticated: true, path: '/story?x=1' }
    expect(previewRedirect({ ...base, preview: false })).toBe('https://ev-monitor.net/story?x=1')
    expect(previewRedirect({ ...base, preview: undefined })).toBe('https://ev-monitor.net/story?x=1')
    expect(previewRedirect({ ...base, preview: true })).toBeNull()
    expect(previewRedirect({ ...base, authenticated: false, preview: false })).toBeNull()
    expect(previewRedirect({ ...base, hostname: 'ev-monitor.net', preview: false })).toBeNull()
  })
})
